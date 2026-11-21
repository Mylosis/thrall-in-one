package com.thrallinone;

import com.google.common.collect.ImmutableSet;
import java.awt.Color;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Reminders: our thrall ran out and we're fighting without one, and
 * (optional) we couldn't summon another because we're on the wrong spellbook
 * or don't have the Book of the Dead; those two show only in fights within
 * 10 minutes of having a thrall out. The casts warning ({@link RuneWarning},
 * no setting of its own) is different: it shows whenever {@link ThrallCasts}
 * raises it, fighting or not, as the sidebar does, whatever Recast Box is set
 * to; out of runes also takes the recast box's place, since there is nothing
 * to recast with. We count as fighting for the configured
 * timeout after any hitsplat on us or by us, and while we're attacking an NPC
 * with a combat level; a TFA boss starting starts one, and dying or leaving a
 * raid ends it. The expiry notification fires once per expiry: when
 * the thrall runs out mid-fight, or when we next start fighting without one.
 * A thrall left behind in another instance ({@link ThrallTracker#isLeftBehind})
 * counts as expired, though the varbit stays on. None of these, bar the casts warning, show where a thrall can't be summoned ({@link NoSummonTracker});
 * an expiry notification held back there fires once we fight somewhere else.
 * The optional Recast Flash comes with the expiry notification (Notify Me).
 * The optional Recast Sound plays once per thrall: a second ahead of the timer running out while
 * we fight, or else with the expiry notification. These only show
 * information; nothing is cast for you.
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
	/** One flash: on and off every 0.8 s for 2 s, like RuneLite's own "Flash 2 s". */
	static final long FLASH_MILLIS = 2000;
	private static final long FLASH_PERIOD_MILLIS = 800;
	/**
	 * Recast Sound plays this many ticks before the thrall runs out
	 * (1.2 s, as the timer shows "1s"): early enough to recast without a gap.
	 */
	private static final int SOUND_LEAD_TICKS = 2;
	/** The casts warning's box. Both say "Thrall", so the box explains itself to a player who forgot the plugin. */
	private static final String NO_RUNES_TEXT = "No Thrall Runes";
	private static final String LOW_RUNES_TEXT = "Low on Thrall Runes";
	/** Changing one of these plays Recast Sound. */
	private static final Set<String> SOUND_KEYS = ImmutableSet.of("recastSound", "recastSoundVolume");
	/** The sound test waits this long after the last change to its setting. */
	private static final long SOUND_TEST_SETTLE_MILLIS = 300;

	private final Client client;
	private final ThrallTracker tracker;
	private final ThrallTimer timer;
	private final ThrallInOneConfig config;
	private final Notifier notifier;
	private final NoSummonTracker noSummon;
	private final TfaHelper tfaHelper;
	private final ThrallSounds sounds;

	/** The resurrection varbit; {@link #thrallOut()} also allows for a thrall left behind. */
	private boolean active;
	/** The tracker's last left-behind tick already counted as an expiry. */
	private int leftBehindSeen = -1;
	/** TFA had our thrall stranded last tick ({@link TfaHelper#isStranded}). */
	private boolean strandedSeen;
	/** The last TFA boss start already counted as the start of a fight. */
	private int bossStartSeen = -1;
	private boolean expiryCheckPending;
	private int lastCombatTick = -1;
	private int expiredTick = -1;
	private boolean expiryNotified;
	private int lastThrallTick = -1;
	private int diedTick = -1;
	/** The raid we stood in last tick, or null. */
	@Nullable
	private String raid;

	private boolean wrongSpellbook;
	private boolean missingBook;
	/** The casts warning; {@link ThrallCasts} sets it as it counts. */
	private RuneWarning runes = RuneWarning.NONE;

	/** The Sound has played for the current thrall (the one from our last cast). */
	private boolean soundPlayed;
	/** Our last cast tick when {@link #soundPlayed} was last cleared: a new cast clears it again. */
	private int soundCastSeen = -1;
	/** When the Flash started, or -1. Read by the flash overlay. */
	private volatile long flashStart = -1;
	/** When to play the sound test for a changed Recast Sound setting, or -1. */
	private volatile long soundTestAt = -1;

	/** The reminder a sidebar preview button is showing, until {@link #previewUntil}. */
	@Nullable
	private volatile PreviewReminder preview;
	private volatile long previewUntil;

	@Inject
	ThrallReminders(Client client, ThrallTracker tracker, ThrallTimer timer,
		ThrallInOneConfig config, Notifier notifier, NoSummonTracker noSummon, TfaHelper tfaHelper, ThrallSounds sounds)
	{
		this.client = client;
		this.tracker = tracker;
		this.timer = timer;
		this.config = config;
		this.notifier = notifier;
		this.noSummon = noSummon;
		this.tfaHelper = tfaHelper;
		this.sounds = sounds;
	}

	void reset()
	{
		active = client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) != 0;
		leftBehindSeen = -1;
		strandedSeen = false;
		bossStartSeen = -1;
		expiryCheckPending = false;
		lastCombatTick = -1;
		expiredTick = -1;
		expiryNotified = false;
		lastThrallTick = active ? client.getTickCount() : -1;
		diedTick = -1;
		raid = null;
		wrongSpellbook = false;
		missingBook = false;
		soundPlayed = false;
		soundCastSeen = tracker.getLastCastTick();
		flashStart = -1;
	}

	public boolean isInCombat()
	{
		return lastCombatTick >= 0 && client.getTickCount() - lastCombatTick <= Fight.secondsToTicks(config.combatTimeoutSeconds());
	}

	/**
	 * Our thrall ran out during a fight and we haven't summoned another yet
	 * (or it's being previewed). Not while the No Thrall Runes box stands in for it.
	 */
	public boolean showExpired()
	{
		return previewing(PreviewReminder.RECAST)
			|| config.recastBox() != RecastBox.OFF
				&& expiredTick >= 0 && !thrallOut() && isInCombat() && canSummonHere() && runes != RuneWarning.OUT;
	}

	/** Client thread. */
	void setRuneWarning(RuneWarning runes)
	{
		this.runes = runes;
	}

	/**
	 * Our thrall is out: the varbit is on and we didn't leave it behind in
	 * another instance, where the varbit can stay on long after it's gone, or
	 * in another of Ocellus's quadrants or outside a TFA boss arena it can't
	 * walk into ({@link TfaHelper}). Left behind or stranded, it counts as
	 * expired.
	 */
	private boolean thrallOut()
	{
		return active && !tracker.isLeftBehind() && !tfaHelper.isStranded();
	}

	public boolean showWrongSpellbook()
	{
		return previewing(PreviewReminder.SPELLBOOK)
			|| config.summonCheck() && wrongSpellbook && warningApplies();
	}

	public boolean showMissingBook()
	{
		return previewing(PreviewReminder.BOOK)
			|| config.bookWarning() && missingBook && warningApplies();
	}

	/**
	 * The casts warning's words, or null while it isn't showing. Unlike the
	 * two warnings above it doesn't wait for a fight or a recent thrall: it
	 * mirrors the sidebar's warning, so a player sees it at the bank, in time
	 * to restock. Only Warn Below 0 turns it off: Recast Box Off doesn't, as
	 * this is the low-runes alert, not a recast reminder. Unlike the rest it
	 * also shows where thralls can't be summoned: banks and lobbies are often
	 * such places (a learned no-summon region, 16470, once showed the sidebar
	 * warning and no box), and that is where a player restocks.
	 */
	@Nullable
	public String runeWarningText()
	{
		if (preview == PreviewReminder.RUNES && previewing(PreviewReminder.RUNES))
		{
			return NO_RUNES_TEXT;
		}
		// All shows the Recast box too, which play only shows beside the low warning.
		if (previewing(PreviewReminder.RUNES))
		{
			return LOW_RUNES_TEXT;
		}
		if (runes == RuneWarning.NONE)
		{
			return null;
		}
		return runes == RuneWarning.OUT ? NO_RUNES_TEXT : LOW_RUNES_TEXT;
	}

	/**
	 * Show a reminder for {@link #PREVIEW_MILLIS}, ignoring the reminder
	 * settings; the recast box (and All) also sends Notify Me, Recast Flash
	 * and Recast Sound, as they're set. Client thread.
	 */
	void preview(PreviewReminder reminder)
	{
		preview = reminder;
		previewUntil = System.currentTimeMillis() + PREVIEW_MILLIS;
		if (reminder.shows(PreviewReminder.RECAST))
		{
			alert();
			playSound();
		}
	}

	/** Notify Me, and Recast Flash if it's on. */
	private void alert()
	{
		// Also sent when a fight starts without a thrall, so not "expired".
		notifier.notify(config.expiryNotification(),
			runes == RuneWarning.OUT ? "Out of runes for your thrall" : "Recast your thrall");
		if (config.recastFlash())
		{
			flashStart = System.currentTimeMillis();
		}
	}

	/** Recast Sound, unless it's Off. Client thread. */
	private void playSound()
	{
		sounds.playReminder(config.recastSound(), config.recastSoundVolume());
	}

	/** The Flash's color this frame, or null when it isn't showing (or is between flashes). */
	@Nullable
	Color flashColor()
	{
		long start = flashStart;
		if (start < 0)
		{
			return null;
		}
		long since = System.currentTimeMillis() - start;
		if (since < 0 || since >= FLASH_MILLIS || !config.recastFlash())
		{
			flashStart = -1;
			return null;
		}
		return since % FLASH_PERIOD_MILLIS < FLASH_PERIOD_MILLIS / 2 ? config.recastFlashColor() : null;
	}

	/**
	 * Picking a Recast Sound or changing its volume plays it, so players can
	 * hear what they chose. Played once the setting has stopped changing for
	 * {@link #SOUND_TEST_SETTLE_MILLIS}, so holding the volume arrow doesn't
	 * stack a sound per step.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ThrallInOneConfig.GROUP.equals(event.getGroup()) && SOUND_KEYS.contains(event.getKey()))
		{
			soundTestAt = System.currentTimeMillis() + SOUND_TEST_SETTLE_MILLIS;
		}
	}

	/** Play the sound test once its setting has settled. Client thread, every client tick. */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		long due = soundTestAt;
		if (due >= 0 && System.currentTimeMillis() >= due)
		{
			soundTestAt = -1;
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				playSound();
			}
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
		if (thrallOut() || !canSummonHere() || !isInCombat())
		{
			return false;
		}
		int sinceThrall = client.getTickCount() - lastThrallTick;
		// Negative if the tick count started again (a relog): not recent then.
		return lastThrallTick >= 0 && sinceThrall >= 0 && sinceThrall <= RECENT_USE_TICKS;
	}

	/** False where thralls can't be summoned: reminders always stay quiet there. */
	private boolean canSummonHere()
	{
		return !noSummon.isNoSummonHere();
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
			// Already counted as expired when it was left behind: one notification per thrall.
			if (!active && !recast && expiredTick < 0)
			{
				expiredTick = tick;
				expiryNotified = false;
				log.debug("Thrall expired at tick {} (in combat: {}, last combat tick {}, cooldown varbit {})",
					tick, isInCombat(), lastCombatTick, client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_COOLDOWN));
			}
		}

		// Left behind in another instance: as good as expired, though the varbit stays on.
		int leftBehind = tracker.getLeftBehindTick();
		if (leftBehind >= 0 && leftBehind != leftBehindSeen)
		{
			leftBehindSeen = leftBehind;
			// Recent use for the warnings: the varbit may not change again for a long while.
			lastThrallTick = leftBehind;
			if (expiredTick < 0)
			{
				expiredTick = leftBehind;
				expiryNotified = false;
				log.debug("Thrall left behind at tick {}: counts as expired (in combat: {}, last combat tick {})",
					leftBehind, isInCombat(), lastCombatTick);
			}
		}

		// Stranded at TFA (another Ocellus quadrant, or outside a boss arena): as good as expired too.
		boolean stranded = active && tfaHelper.isStranded();
		if (stranded && !strandedSeen)
		{
			lastThrallTick = tick;
			if (expiredTick < 0)
			{
				expiredTick = tick;
				expiryNotified = false;
				log.debug("Thrall stranded at TFA at tick {}: counts as expired (in combat: {})", tick, isInCombat());
			}
		}
		else if (!stranded && strandedSeen && active && !tracker.isLeftBehind()
			&& expiredTick >= 0 && tracker.getLastCastTick() < expiredTick)
		{
			// It reached us after all, with no cast: it never really went.
			log.debug("Thrall no longer stranded at tick {}", tick);
			expiredTick = -1;
			soundPlayed = false;
		}
		strandedSeen = stranded;

		// Recast since the expiry: our cast graphic shows at once, the varbit
		// only about 5 ticks later, so don't wait for it to clear the reminder.
		if (expiredTick >= 0 && tracker.getLastCastTick() >= expiredTick)
		{
			log.debug("Recast at tick {} after the expiry at tick {}", tracker.getLastCastTick(), expiredTick);
			expiredTick = -1;
		}

		// Teleporting out of a raid (or a wipe) ends the fight: a thrall left
		// behind there would otherwise bring up the reminder outside.
		String raidNow = BossKey.current(client);
		if (raid != null && !raid.equals(raidNow) && lastCombatTick >= 0)
		{
			lastCombatTick = -1;
			log.debug("Left {} at tick {}; no longer in combat", raid, tick);
		}
		raid = raidNow;

		// A TFA boss starting is the start of a fight, so a thrall that ran out
		// between rooms is reminded before the first hit.
		int bossStart = tfaHelper.getBossStartTick();
		if (bossStart >= 0 && bossStart != bossStartSeen)
		{
			bossStartSeen = bossStart;
			if (!justDied(tick) && bossStart > lastCombatTick)
			{
				lastCombatTick = bossStart;
			}
		}

		Player me = client.getLocalPlayer();
		Actor target = me == null ? null : me.getInteracting();
		if (target instanceof NPC && ((NPC) target).getCombatLevel() > 0 && !justDied(tick))
		{
			lastCombatTick = tick;
		}

		// A new cast is a new thrall, with its own Sound to come.
		int castTick = tracker.getLastCastTick();
		if (castTick != soundCastSeen)
		{
			soundCastSeen = castTick;
			soundPlayed = false;
		}

		// Recast Sound, a second ahead of the timer running out while we fight.
		if (!soundPlayed && config.recastSound() != ReminderSound.OFF
			&& thrallOut() && isInCombat() && canSummonHere())
		{
			int remaining = timer.getRemainingTicks();
			if (remaining > 0 && remaining <= SOUND_LEAD_TICKS)
			{
				soundPlayed = true;
				log.debug("Reminder sound at tick {}, {} ticks before the expiry", tick, remaining);
				playSound();
			}
		}

		// Once per expiry, as soon as we're fighting without a thrall somewhere we could summon one.
		if (expiredTick >= 0 && !thrallOut() && !expiryNotified && isInCombat() && canSummonHere())
		{
			expiryNotified = true;
			log.debug("Expiry alert at tick {}, {} ticks after the expiry", tick, tick - expiredTick);
			alert();
			// Unless it already played ahead of the expiry.
			if (!soundPlayed)
			{
				soundPlayed = true;
				playSound();
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
