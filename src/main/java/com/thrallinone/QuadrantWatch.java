package com.thrallinone;

import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Whether our thrall is stranded in another Ocellus quadrant. Ticks and
 * quadrants are passed in, so the rules can be unit tested without the
 * client.
 *
 * <p>It decides only when we land in a quadrant (our quadrant changes), never
 * on anything Ocellus does: landing with ours in another quadrant strands it
 * until we cast or it turns up beside us. The first quadrant we're seen in
 * counts as a landing too.
 */
final class QuadrantWatch
{
	/** Our quadrant as last seen, or null before we've been seen in one. */
	@Getter
	@Nullable
	private OcellusQuadrant ours;
	/** Tick we last landed away from our thrall, or -1 while it's with us. */
	@Getter
	private int strandedTick = -1;

	/**
	 * Once a tick in phases 3 and 4. {@code here} is our quadrant (null near
	 * the lines between them, which keeps the last one), {@code thrall} our
	 * thrall's, or null if none of ours is out. True on each landing that
	 * leaves our thrall behind.
	 */
	boolean tick(int tick, @Nullable OcellusQuadrant here, @Nullable OcellusQuadrant thrall)
	{
		if (here != null && here != ours)
		{
			ours = here;
			// With none out there's nothing to strand: the usual expiry reminder covers it.
			if (thrall == null || thrall == here)
			{
				strandedTick = -1;
				return false;
			}
			strandedTick = tick;
			return true;
		}
		// It can't cross, but if it's beside us after all (a new thrall), it isn't stranded.
		if (strandedTick >= 0 && thrall != null && thrall == ours)
		{
			strandedTick = -1;
		}
		return false;
	}

	/** We cast on this tick (our cast graphic): a cast after landing ends it. */
	void cast(int castTick)
	{
		if (strandedTick >= 0 && castTick >= strandedTick)
		{
			strandedTick = -1;
		}
	}

	boolean isStranded()
	{
		return strandedTick >= 0;
	}

	/** Out of phases 3 and 4, out of the arena, dead, or logged out. */
	void reset()
	{
		ours = null;
		strandedTick = -1;
	}
}
