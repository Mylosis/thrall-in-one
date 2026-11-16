package com.thrallinone;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.eventbus.Subscribe;

/**
 * Reminders: our thrall ran out and we're fighting without one, and
 * (optional) we couldn't summon another because we're on the wrong spellbook
 * or don't have the Book of the Dead; those two show only in fights within
 * 10 minutes of having a thrall out. We count as fighting for the configured
 * timeout after any hitsplat on us or by us, and while we're attacking an NPC
 * with a combat level; dying ends the fight. The expiry notification fires once per expiry: when
 * the thrall runs out mid-fight, or when we next start fighting without one.
 * None of these show where a thrall can't be summoned ({@link NoSummonTracker});
 * an expiry notification held back there fires once we fight somewhere else.
 * These only show information; nothing is cast for you.
 */
@Slf4j
@Singleton
public class ThrallReminders
{
	/** The can't-summon warnings show in fights up to this long after our last thrall (10 minutes). */
	private static final int RECENT_USE_TICKS = 1000;
	/** {@link VarbitID#SPELLBOOK} on the Arceuus spellbook (no gameval names its values). */
	static final int ARCEUUS_SPELLBOOK = 3;
	/** How long a preview from the sidebar shows. */
	static final long PREVIEW_MILLIS = 10_000;
	/** Hits landing this soon after we die don't start a new fight. */
	private static final int DEATH_GRACE_TICKS = 3;

	private final Client client;
	private final ThrallTracker tracker;
	private final ThrallInOneConfig config;
	private final Notifier notifier;
	private final NoSummonTracker noSummon;

	private boolean active;
	private boolean expiryCheckPending;
	private int lastCombatTick = -1;
	private int expiredTick = -1;
	private boolean expiryNotified;
	private int lastThrallTick = -1;
	private int diedTick = -1;

	private boolean wrongSpellbook;
	private boolean missingBook;

	/** The reminder a sidebar preview button is showing, until {@link #previewUntil}. */
	@Nullable
	private volatile PreviewReminder preview;
	private volatile long previewUntil;

	@Inject
	ThrallReminders(Client client, ThrallTracker tracker, ThrallInOneConfig config, Notifier notifier,
		NoSummonTracker noSummon)
	{
		this.client = client;
		this.tracker = tracker;
		this.config = config;
		this.notifier = notifier;
		this.noSummon = noSummon;
	}

	void reset()
	{
		active = client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) != 0;
		expiryCheckPending = false;
		lastCombatTick = -1;
		expiredTick = -1;
		expiryNotified = false;
		lastThrallTick = active ? client.getTickCount() : -1;
		diedTick = -1;
		wrongSpellbook = false;
		missingBook = false;
	}

	public boolean isInCombat()
	{
		return lastCombatTick >= 0 && client.getTickCount() - lastCombatTick <= Fight.secondsToTicks(config.combatTimeoutSeconds());
	}

	/** Our thrall ran out during a fight and we haven't summoned another yet (or it's being previewed). */
	public boolean showExpired()
	{
		return previewing(PreviewReminder.RECAST)
			|| config.remindersEnabled() && config.recastBox() != RecastBox.OFF
				&& expiredTick >= 0 && !active && isInCombat() && canSummonHere();
	}

	public boolean showWrongSpellbook()
	{
		return previewing(PreviewReminder.SPELLBOOK)
			|| config.remindersEnabled() && config.summonCheck() && wrongSpellbook && warningApplies();
	}

	public boolean showMissingBook()
	{
		return previewing(PreviewReminder.BOOK)
			|| config.remindersEnabled() && config.bookWarning() && missingBook && warningApplies();
	}

	/**
	 * Show a reminder for {@link #PREVIEW_MILLIS}, ignoring the reminder
	 * settings; the recast box (and All) also sends the expiry notification.
	 * Client thread.
	 */
	void preview(PreviewReminder reminder)
	{
		preview = reminder;
		previewUntil = System.currentTimeMillis() + PREVIEW_MILLIS;
		if (reminder.shows(PreviewReminder.RECAST))
		{
			notifier.notify(config.expiryNotification(), "Your thrall has expired");
		}
	}

	private boolean previewing(PreviewReminder reminder)
	{
		PreviewReminder shown = preview;
		return shown != null && shown.shows(reminder) && System.currentTimeMillis() < previewUntil;
	}

	/**
	 * Warn that we can't summon a thrall only while it would matter: no
	 * thrall out, fighting, and using thralls lately. A player who doesn't
	 * use thralls never sees these, and after a trip they stay quiet at the
	 * bank.
	 */
	private boolean warningApplies()
	{
		if (active || !canSummonHere() || !isInCombat())
		{
			return false;
		}
		int sinceThrall = client.getTickCount() - lastThrallTick;
		// Negative if the tick count started again (a relog): not recent then.
		return lastThrallTick >= 0 && sinceThrall >= 0 && sinceThrall <= RECENT_USE_TICKS;
	}

	/** False where thralls can't be summoned (and the setting to stay quiet there is on). */
	private boolean canSummonHere()
	{
		return !config.quietWhereNoSummon() || !noSummon.isNoSummonHere();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player me = client.getLocalPlayer();
		int tick = client.getTickCount();
		if ((event.getActor() == me || event.getHitsplat().isMine()) && !justDied(tick))
		{
			lastCombatTick = tick;
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			diedTick = client.getTickCount();
			lastCombatTick = -1;
			log.debug("We died at tick {}; no longer in combat", diedTick);
		}
	}

	/** Hits still landing just after we died don't start a new fight. */
	private boolean justDied(int tick)
	{
		return diedTick >= 0 && tick >= diedTick && tick - diedTick <= DEATH_GRACE_TICKS;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.ARCEUUS_RESURRECTION_ACTIVE)
		{
			return;
		}

		boolean nowActive = event.getValue() != 0;
		if (nowActive == active)
		{
			return;
		}
		active = nowActive;
		lastThrallTick = client.getTickCount();
		if (active)
		{
			expiredTick = -1;
		}
		else
		{
			// Decide at the end of the tick, once a recast in the same tick
			// (which also clears the varbit) has shown its cast graphic.
			expiryCheckPending = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		if (expiryCheckPending)
		{
			expiryCheckPending = false;
			boolean recast = tracker.getLastCastTick() >= 0 && tick - tracker.getLastCastTick() <= 1;
			if (!active && !recast)
			{
				expiredTick = tick;
				expiryNotified = false;
				log.debug("Thrall expired at tick {} (in combat: {}, last combat tick {}, cooldown varbit {})",
					tick, isInCombat(), lastCombatTick, client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_COOLDOWN));
			}
		}

		// Recast since the expiry: our cast graphic shows at once, the varbit
		// only about 5 ticks later, so don't wait for it to clear the reminder.
		if (expiredTick >= 0 && tracker.getLastCastTick() >= expiredTick)
		{
			log.debug("Recast at tick {} after the expiry at tick {}", tracker.getLastCastTick(), expiredTick);
			expiredTick = -1;
		}

		Player me = client.getLocalPlayer();
		Actor target = me == null ? null : me.getInteracting();
		if (target instanceof NPC && ((NPC) target).getCombatLevel() > 0 && !justDied(tick))
		{
			lastCombatTick = tick;
		}

		// Once per expiry, as soon as we're fighting without a thrall somewhere we could summon one.
		// With reminders off it still counts as sent, so turning them back on mid-fight doesn't fire a stale one.
		if (expiredTick >= 0 && !active && !expiryNotified && isInCombat() && canSummonHere())
		{
			expiryNotified = true;
			if (config.remindersEnabled())
			{
				log.debug("Expiry notification at tick {}, {} ticks after the expiry", tick, tick - expiredTick);
				notifier.notify(config.expiryNotification(), "Your thrall has expired");
			}
		}

		wrongSpellbook = config.summonCheck() && client.getVarbitValue(VarbitID.SPELLBOOK) != ARCEUUS_SPELLBOOK;
		missingBook = config.bookWarning() && !contains(InventoryID.WORN) && !contains(InventoryID.INV);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				active = false;
				expiryCheckPending = false;
				expiredTick = -1;
				lastCombatTick = -1;
				break;
			default:
				break;
		}
	}

	private boolean contains(int inventoryId)
	{
		ItemContainer container = client.getItemContainer(inventoryId);
		return container != null && container.contains(ItemID.BOOK_OF_THE_DEAD);
	}
}
