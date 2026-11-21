package com.thrallinone;

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.game.ItemManager;

/**
 * Counts our casts and adds each summon to the saved records: which thrall it
 * raised, its prayer, and what it cost. Driven once a tick by
 * {@link ThrallFights}, which owns the records and the panel updates.
 *
 * <p>The runes that leave the inventory or rune pouch between the tick before
 * the cast and the tick after it are measured (staves, tomes and combination
 * runes change them) only to price the cast; the records keep the cost, not
 * the runes.
 */
@Slf4j
@Singleton
class ThrallSummons
{
	/** Give up working out which thrall a cast raised after this many ticks. */
	private static final int SUMMON_RESOLVE_TICKS = 10;

	private final Client client;
	private final ThrallTracker tracker;
	private final ThrallStatsStore store;
	private final ItemManager itemManager;

	/** Casts this login session. */
	@Getter
	private int sessionCasts;
	private int lastSeenCastTick = -1;
	@Nullable
	private PendingSummon pending;
	@Nullable
	private NPC ownedLastTick;
	private Map<Integer, Integer> runesLastTick = Collections.emptyMap();
	/**
	 * The inventory and rune pouch as last read ({@link RuneCounts#read}),
	 * reused until either changes: reading them builds a map, and most ticks
	 * nothing changed. Never modified once read. Null: read on the next tick.
	 */
	@Nullable
	private Map<Integer, Integer> runesNow;
	/** The inventory changed since {@link #runesNow} was read. */
	private boolean inventoryChanged = true;
	/** The rune pouch's raw slots when {@link #runesNow} was read. */
	private final int[] pouchSeen = new int[RunePouch.RAW_SIZE];

	@Inject
	ThrallSummons(Client client, ThrallTracker tracker, ThrallStatsStore store, ItemManager itemManager)
	{
		this.client = client;
		this.tracker = tracker;
		this.store = store;
		this.itemManager = itemManager;
	}

	void reset()
	{
		sessionCasts = 0;
		lastSeenCastTick = tracker.getLastCastTick();
		pending = null;
		ownedLastTick = tracker.getOwned();
		runesLastTick = Collections.emptyMap();
		runesNow = null;
	}

	/** The inventory changed: read the runes afresh on the next tick. Client thread. */
	void inventoryChanged()
	{
		inventoryChanged = true;
	}

	/** Logged out or hopped: the tick count can start again after this. */
	void loggedOut(boolean newSession)
	{
		runesNow = null;
		pending = null;
		lastSeenCastTick = -1;
		if (newSession)
		{
			sessionCasts = 0;
		}
	}

	/**
	 * Once a game tick. Returns what changed: {@link Change#CAST} when a new
	 * cast was counted, {@link Change#SUMMON} when a summon was added to the
	 * records (and saved).
	 */
	Change tick(int tick)
	{
		Map<Integer, Integer> runes = currentRunes();
		boolean cast = noteCast(tick);
		boolean summoned = resolveSummon(tick, runes);
		runesLastTick = runes;
		ownedLastTick = tracker.getOwned();
		return summoned ? Change.SUMMON : cast ? Change.CAST : Change.NONE;
	}

	/**
	 * What {@link RuneCounts#read} would return now: read again only when the
	 * inventory changed (an event) or the pouch's slots did (compared here, a
	 * few varbit reads), otherwise the last read, which is the same.
	 */
	private Map<Integer, Integer> currentRunes()
	{
		boolean pouchChanged = RunePouch.readRaw(client, pouchSeen);
		Map<Integer, Integer> runes = runesNow;
		if (runes == null || inventoryChanged || pouchChanged)
		{
			inventoryChanged = false;
			runes = RuneCounts.read(client);
			runesNow = runes;
		}
		return runes;
	}

	enum Change
	{
		NONE,
		CAST,
		SUMMON,
	}

	/** Notice a new cast of ours from the tracker's cast graphic (which reports each cast once). */
	private boolean noteCast(int tick)
	{
		int castTick = tracker.getLastCastTick();
		if (castTick < 0 || castTick == lastSeenCastTick)
		{
			return false;
		}
		lastSeenCastTick = castTick;
		sessionCasts++;
		pending = new PendingSummon(castTick, runesLastTick, ownedLastTick);
		log.debug("Thrall cast at tick {} (seen at tick {})", castTick, tick);
		return true;
	}

	/** Once the cast's thrall is known and the runes have had a tick to leave the inventory, add the summon to the records. */
	private boolean resolveSummon(int tick, Map<Integer, Integer> runes)
	{
		if (pending == null)
		{
			return false;
		}
		if (tick == pending.castTick + 1)
		{
			pending.after = runes;
		}

		NPC owned = tracker.getOwned();
		boolean raised = owned != null && owned != pending.ownedBefore;
		if (pending.after == null || !raised && tick - pending.castTick < SUMMON_RESOLVE_TICKS)
		{
			return false;
		}

		ThrallType type = raised ? ThrallType.of(owned.getId()) : null;
		ThrallTier tier = raised ? ThrallTier.of(owned.getId()) : null;
		Map<Integer, Integer> used = tier == null
			? Collections.emptyMap()
			: RuneCounts.usedByCast(tier, pending.before, pending.after);
		long gp = 0;
		for (Map.Entry<Integer, Integer> rune : used.entrySet())
		{
			gp += (long) itemManager.getItemPrice(rune.getKey()) * rune.getValue();
		}
		log.debug("Summon at tick {}: {} {}, runes {}, {} gp", pending.castTick, tier, type, used, gp);
		pending = null;

		store.getRecords().addSummon(type, tier, gp);
		store.save();
		return true;
	}

	private static final class PendingSummon
	{
		final int castTick;
		final Map<Integer, Integer> before;
		@Nullable
		final NPC ownedBefore;
		@Nullable
		Map<Integer, Integer> after;

		PendingSummon(int castTick, Map<Integer, Integer> before, @Nullable NPC ownedBefore)
		{
			this.castTick = castTick;
			this.before = before;
			this.ownedBefore = ownedBefore;
		}
	}
}
