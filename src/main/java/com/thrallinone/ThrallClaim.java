package com.thrallinone;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Pure decision rules for "which of these thralls is mine". Kept free of the
 * client API so the rules can be unit tested; {@link ThrallTracker} gathers
 * the facts about each thrall in the scene and asks this class to choose.
 *
 * <p>Thralls have no owner field, so ownership is inferred from three signals,
 * strongest first:
 * <ol>
 * <li>the thrall is interacting with (following or facing) the local player,
 * and a thrall interacting with some other player is never ours;</li>
 * <li>timing: it appeared close to us within a few ticks of our resurrection
 * varbit turning on;</li>
 * <li>position: while our varbit is on and we have no thrall yet, one that is
 * near us and nearer to us than to any other player.</li>
 * </ol>
 * In game an idle thrall's interacting target is null and a fighting one's is
 * the NPC it attacks, so the first signal rarely fires: timing and position
 * do most of the work (with our cast graphic, see {@link ThrallTracker}).
 */
final class ThrallClaim
{
	/**
	 * The thrall NPC appears before the varbit turns on: measured in game it
	 * spawned 5 ticks earlier (the varbit is set once it has risen).
	 */
	static final int EXPECTED_SPAWN_OFFSET = -5;
	/** A spawn this many ticks before our varbit turned on can still be our cast. */
	static final int CAST_WINDOW_BEFORE_TICKS = 8;
	/** Allowance for the spawn arriving after the varbit instead. */
	static final int CAST_WINDOW_AFTER_TICKS = 3;
	/** A fresh thrall appears close to its caster. */
	static final int CAST_RADIUS = 3;
	/** Our thrall follows us, so when we look for it by position it is close, but not always adjacent. */
	static final int REACQUIRE_RADIUS = 10;

	enum Mode
	{
		/** Our varbit just turned on: look for the thrall that spawned with it. */
		FRESH_CAST,
		/** Varbit on but no thrall claimed (missed cast, scene reload, left render range). */
		REACQUIRE,
		/** We teleported away from our thrall: only accept one that is interacting with us. */
		INTERACTING_ONLY,
	}

	@Value
	static class Candidate<T>
	{
		T thrall;
		/** Chebyshev tile distance from the local player. */
		int distance;
		boolean interactingWithMe;
		boolean interactingWithOtherPlayer;
		/** Another player stands at least as close to this thrall as we do. */
		boolean otherPlayerAsClose;
		/** Ticks between the varbit turning on and this thrall appearing; negative if it appeared first. */
		int spawnTickOffset;
	}

	private ThrallClaim()
	{
	}

	/**
	 * We already hold a thrall claimed from this same cast, and another of the
	 * cast's type appears beside us (someone near us cast the same spell at
	 * about the same time). Take the newcomer only if it fits our cast better:
	 * ours spawns in the tick of our cast graphic (measured in game), so the
	 * spawn closer to the cast wins, then the nearer one; a tie keeps the one
	 * we hold. Offsets are spawn tick minus cast tick.
	 */
	static boolean replacesCastClaim(int heldSpawnOffset, int heldDistance, int newSpawnOffset, int newDistance)
	{
		int held = Math.abs(heldSpawnOffset);
		int fresh = Math.abs(newSpawnOffset);
		if (fresh != held)
		{
			return fresh < held;
		}
		return newDistance < heldDistance;
	}

	static boolean isInCastWindow(int spawnTickOffset)
	{
		return spawnTickOffset >= -CAST_WINDOW_BEFORE_TICKS && spawnTickOffset <= CAST_WINDOW_AFTER_TICKS;
	}

	@Nullable
	static <T> Candidate<T> pick(List<Candidate<T>> candidates, Mode mode)
	{
		List<Candidate<T>> eligible = candidates.stream()
			.filter(c -> !c.isInteractingWithOtherPlayer())
			.collect(Collectors.toList());

		Candidate<T> following = eligible.stream()
			.filter(Candidate::isInteractingWithMe)
			.min(Comparator.comparingInt(Candidate::getDistance))
			.orElse(null);
		if (following != null || mode == Mode.INTERACTING_ONLY)
		{
			return following;
		}

		if (mode == Mode.FRESH_CAST)
		{
			List<Candidate<T>> spawnedWithCast = eligible.stream()
				.filter(c -> isInCastWindow(c.getSpawnTickOffset()))
				.filter(c -> c.getDistance() <= CAST_RADIUS)
				.collect(Collectors.toList());
			if (spawnedWithCast.size() == 1)
			{
				// One thrall appeared next to us as our varbit turned on: that's
				// ours even if someone else is stacked on the same tile.
				return spawnedWithCast.get(0);
			}
			// Several casts landed together (a stacked raid lobby): prefer the
			// one nobody else is as close to, then the nearest, then the one
			// whose spawn lines up best with our varbit.
			return spawnedWithCast.stream()
				.min(Comparator.<Candidate<T>, Boolean>comparing(Candidate::isOtherPlayerAsClose)
					.thenComparingInt(Candidate::getDistance)
					.thenComparingInt(c -> Math.abs(c.getSpawnTickOffset() - EXPECTED_SPAWN_OFFSET)))
				.orElse(null);
		}

		// REACQUIRE: a thrall stays beside its owner, so one that is nearer to
		// us than to anybody else is ours.
		return eligible.stream()
			.filter(c -> c.getDistance() <= REACQUIRE_RADIUS)
			.filter(c -> !c.isOtherPlayerAsClose())
			.min(Comparator.comparingInt(Candidate::getDistance))
			.orElse(null);
	}
}
