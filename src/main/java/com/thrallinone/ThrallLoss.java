package com.thrallinone;

import lombok.Getter;

/**
 * Why our claimed thrall's NPC went away while the resurrection varbit was
 * still on, and whether it now counts as left behind. Ticks are passed in,
 * so the rules can be unit tested without the client.
 *
 * <p>Every room of The Fractured Archive is its own instance. On entering the
 * next room our thrall stays in the one we left, yet the varbit stays on,
 * even past the thrall's expiry, until the next cast. The game lets us
 * recast meanwhile. So a thrall
 * lost to a scene change that none of ours replaces within
 * {@link #GRACE_TICKS} counts as left behind: not out, whatever the varbit
 * says. A thrall that despawns with its timer at or near zero ran out
 * instead, and nothing else should be claimed in its place. Anything else
 * (it walked out of view) may come back, as before.
 */
final class ThrallLoss
{
	/** A thrall lost to a scene change has this long to come back (it follows us across ordinary map loads). */
	static final int GRACE_TICKS = 5;
	/** Measured: our thrall despawns at expiry with 2 ticks left on the timer, and the varbit clears a tick later. */
	static final int EXPIRY_TICKS = 3;

	enum Kind
	{
		NONE,
		/** Lost while the scene loaded or we teleported: left behind unless it comes back. */
		SCENE_CHANGE,
		/** Despawned with the timer at or near zero: it ran out. */
		EXPIRY,
		/** Despawned for another reason (out of view): it may come back. */
		OUT_OF_VIEW,
	}

	@Getter
	private Kind kind = Kind.NONE;
	/** Tick our thrall was lost, or -1. */
	@Getter
	private int lostTick = -1;
	/** Tick the lost thrall began to count as left behind, or -1. */
	@Getter
	private int leftBehindTick = -1;

	/**
	 * Our claimed thrall went away with the varbit on. A scene change wins
	 * over the timer, which may be a tick or two off: a thrall left behind
	 * just before its expiry isn't coming back either.
	 */
	Kind lost(int tick, boolean sceneChange, int remainingTicks)
	{
		reset();
		lostTick = tick;
		if (sceneChange)
		{
			kind = Kind.SCENE_CHANGE;
		}
		else if (remainingTicks >= 0 && remainingTicks <= EXPIRY_TICKS)
		{
			kind = Kind.EXPIRY;
		}
		else
		{
			kind = Kind.OUT_OF_VIEW;
		}
		return kind;
	}

	/** The scene is loading: a thrall that was out of view is now in another scene. */
	void sceneChanged(int tick)
	{
		if (kind == Kind.OUT_OF_VIEW)
		{
			kind = Kind.SCENE_CHANGE;
			lostTick = tick;
		}
	}

	/** Once a tick; true on the tick the lost thrall starts to count as left behind. */
	boolean tick(int tick)
	{
		if (kind == Kind.SCENE_CHANGE && leftBehindTick < 0 && tick - lostTick >= GRACE_TICKS)
		{
			leftBehindTick = tick;
			return true;
		}
		return false;
	}

	/** A thrall of ours was claimed, the varbit changed, or the tracker reset. */
	void reset()
	{
		kind = Kind.NONE;
		lostTick = -1;
		leftBehindTick = -1;
	}

	boolean isLeftBehind()
	{
		return leftBehindTick >= 0;
	}

	/** Our thrall ran out: claim nothing in its place until the varbit changes or we cast. */
	boolean isExpired()
	{
		return kind == Kind.EXPIRY;
	}
}
