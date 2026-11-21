package com.thrallinone;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Splits our combat into fights and keeps their stats: uptime, the thrall's
 * share of our damage and its DPS, added up for the session (shown in the
 * sidebar panel) and fed into the saved records. Summons and their cost are
 * counted by {@link ThrallSummons}, driven from here.
 *
 * <p>A fight starts with our first hitsplat on an NPC (ours or our thrall's)
 * and ends when the NPC we hit most dies and nothing else is hit for a few
 * ticks, when we die, when we leave the raid it started in, or when nothing
 * has been hit for the "Fight ends after" time, which the reminders use too. Fights are only saved while we use
 * thralls: our thrall was out during the fight, or in the 10 minutes before
 * it, so a fight where we forgot to cast still counts against uptime. A fight
 * that starts where thralls can't be summoned (Nightmare Zone, a boat, a
 * learned spot) counts only if our thrall was out during it.
 */
@Slf4j
@Singleton
public class ThrallFights
{
	/** Ticks after the main target dies with no other hits before the fight ends. */
	private static final int DEATH_GRACE_TICKS = 3;
	/** A fight counts if our thrall was out this long before it (10 minutes). */
	private static final int RECENT_THRALL_TICKS = 1000;

	private final Client client;
	private final ThrallTracker tracker;
	private final ThrallInOneConfig config;
	private final ThrallStatsStore store;
	private final ThrallSummons summons;
	private final NoSummonTracker noSummon;

	/** The fight going on now, or null. */
	@Nullable
	private Fight current;
	/** Finished fights this login session that count (see the class comment). */
	private FightTotals session = new FightTotals();

	private int lastThrallOutTick = -1;
	private boolean thrallOutBeforeFight;
	private int targetDiedTick = -1;
	/** Tick we last died, or -1. */
	private int diedTick = -1;

	/** Where updates go; null while the sidebar panel is off. */
	@Nullable
	private volatile ThrallPanel panel;
	/** The session or the saved records changed since the panel last got them. */
	private boolean sessionChanged;
	private boolean recordsChanged;

	@Inject
	ThrallFights(Client client, ThrallTracker tracker, ThrallInOneConfig config, ThrallStatsStore store,
		ThrallSummons summons, NoSummonTracker noSummon)
	{
		this.client = client;
		this.tracker = tracker;
		this.config = config;
		this.store = store;
		this.summons = summons;
		this.noSummon = noSummon;
	}

	/** Start afresh and load the account's records. Client thread. */
	void reset()
	{
		current = null;
		session = new FightTotals();
		lastThrallOutTick = -1;
		targetDiedTick = -1;
		diedTick = -1;
		summons.reset();
		store.load();
		sessionChanged = true;
		recordsChanged = true;
		pushToPanel();
	}

	/** Save the fight in progress, if any (the plugin is stopping). Client thread. */
	void stop()
	{
		if (current != null)
		{
			finish();
		}
	}

	void setPanel(@Nullable ThrallPanel panel)
	{
		this.panel = panel;
		sessionChanged = true;
		recordsChanged = true;
	}

	/** Push everything to a freshly shown panel. Client thread. */
	void refreshPanel()
	{
		sessionChanged = true;
		recordsChanged = true;
		pushToPanel();
	}

	/** Clear the account's saved records. Client thread. */
	void resetRecords()
	{
		store.reset();
		recordsChanged = true;
		pushToPanel();
	}

	private boolean thrallOut()
	{
		return tracker.hasActiveThrall() || tracker.getOwned() != null;
	}

	@Nullable
	private ThrallTier ownedTier()
	{
		NPC owned = tracker.getOwned();
		return owned == null ? null : ThrallTier.of(owned.getId());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor target = event.getActor();
		int tick = client.getTickCount();
		if (!event.getHitsplat().isMine() || !(target instanceof NPC) || !isNamed(target.getName()) || justDied(tick))
		{
			return;
		}
		fightAt(tick).hit(tick, target, target.getName(), event.getHitsplat().getAmount());
	}

	/**
	 * Unnamed NPCs are hidden helpers we never attack, like the body behind
	 * Ocellus's second phase. Our hitsplats on them are bookkeeping, not damage,
	 * and would otherwise count as fights of their own.
	 */
	static boolean isNamed(@Nullable String name)
	{
		return name != null && !name.isEmpty() && !"null".equals(name);
	}

	/**
	 * One of our hitsplats that {@link ThrallDamage} matched to our thrall's attack,
	 * with the type of the thrall that attacked (it may have run out since).
	 */
	void onThrallHit(int damage, int tick, @Nullable ThrallType type)
	{
		if (justDied(tick))
		{
			return;
		}
		fightAt(tick).thrallHit(tick, type, damage);
	}

	/** Hits still landing just after we died belong to the fight that ended with the death. */
	private boolean justDied(int tick)
	{
		return diedTick >= 0 && tick >= diedTick && tick - diedTick <= DEATH_GRACE_TICKS;
	}

	/** The fight going on, starting one if there is none. */
	private Fight fightAt(int tick)
	{
		if (current == null)
		{
			current = new Fight(tick);
			current.setRaid(BossKey.current(client));
			boolean recentThrall = lastThrallOutTick >= 0 && tick >= lastThrallOutTick && tick - lastThrallOutTick <= RECENT_THRALL_TICKS;
			// Where no thrall can be summoned, not having one out isn't a lapse.
			thrallOutBeforeFight = thrallOut() || recentThrall && !noSummon.isNoSummonHere();
			targetDiedTick = -1;
			if (log.isDebugEnabled())
			{
				log.debug("Fight started at tick {} (raid {}, template region {})", tick, current.getRaid(), BossKey.templateRegion(client));
			}
		}
		return current;
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			diedTick = client.getTickCount();
			if (current != null)
			{
				log.debug("We died at tick {}", diedTick);
				finish();
				pushToPanel();
			}
		}
		else if (current != null && event.getActor() == current.getMainTarget())
		{
			targetDiedTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		boolean thrallOut = thrallOut();
		if (thrallOut)
		{
			lastThrallOutTick = tick;
		}
		ThrallSummons.Change change = summons.tick(tick);
		if (change != ThrallSummons.Change.NONE)
		{
			sessionChanged = true;
		}
		if (change == ThrallSummons.Change.SUMMON)
		{
			recordsChanged = true;
		}

		if (current != null && leftRaid())
		{
			// Teleported out or wiped: the ticks outside the raid aren't part of the fight.
			log.debug("Left {} at tick {}; fight over", current.getRaid(), tick);
			finish();
		}
		if (current != null)
		{
			current.tick(tick, thrallOut, ownedTier());
			if (fightOver(tick))
			{
				finish();
			}
		}

		pushToPanel();
	}

	/** The fight started in a raid and we're no longer in it. */
	private boolean leftRaid()
	{
		String raid = current.getRaid();
		return raid != null && !raid.equals(BossKey.current(client));
	}

	private boolean fightOver(int tick)
	{
		int sinceLastHit = tick - current.getLastHitTick();
		if (sinceLastHit > Fight.secondsToTicks(config.combatTimeoutSeconds()))
		{
			return true;
		}
		if (targetDiedTick >= 0 && tick - targetDiedTick >= DEATH_GRACE_TICKS)
		{
			// A hit landing a tick after the death was already on its way.
			if (current.getLastHitTick() <= targetDiedTick + 1)
			{
				return true;
			}
			// We moved straight on to another target: the fight carries on.
			targetDiedTick = -1;
		}
		return false;
	}

	private void finish()
	{
		Fight fight = current;
		current = null;
		targetDiedTick = -1;
		FightRecord record = fight.toRecord();
		boolean counts = thrallOutBeforeFight || fight.getThrallTicks() > 0;
		log.debug("Fight ended: {} ({} ticks, thrall out {}), thrall {} of {} damage, saved {}",
			record.getBoss(), record.getTicks(), record.getThrallTicks(), record.getThrallDamage(),
			record.getThrallDamage() + record.getOwnDamage(), counts);
		if (!counts)
		{
			return;
		}

		session.add(record);
		sessionChanged = true;
		store.getRecords().addFight(record);
		store.save();
		recordsChanged = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			summons.inventoryChanged();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Logins, hops and loads can swap the inventory without an event we rely on: read it afresh.
		summons.inventoryChanged();
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				if (current != null)
				{
					finish();
				}
				// The tick count can start again after this.
				diedTick = -1;
				summons.loggedOut(event.getGameState() == GameState.LOGIN_SCREEN);
				if (event.getGameState() == GameState.LOGIN_SCREEN)
				{
					session = new FightTotals();
					sessionChanged = true;
					lastThrallOutTick = -1;
				}
				pushToPanel();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		store.load();
		recordsChanged = true;
		pushToPanel();
	}

	/**
	 * Send the panel what changed: the session when a fight ends or we cast,
	 * the records when they're saved. Copies are made here, on the client thread.
	 */
	private void pushToPanel()
	{
		ThrallPanel target = panel;
		if (target == null)
		{
			return;
		}
		if (sessionChanged)
		{
			sessionChanged = false;
			FightTotals sessionCopy = new FightTotals(session);
			int casts = summons.getSessionCasts();
			SwingUtilities.invokeLater(() -> target.showSession(sessionCopy, casts));
		}
		if (recordsChanged)
		{
			recordsChanged = false;
			ThrallRecords records = store.copy();
			SwingUtilities.invokeLater(() -> target.showRecords(records));
		}
	}
}
