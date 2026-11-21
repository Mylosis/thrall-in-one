package com.thrallinone;

import javax.annotation.Nullable;

/**
 * The four corner quadrants of Ocellus's arena in The Fractured Archive,
 * told apart by template coordinates (the same in every raid, unlike the
 * instance's). In phases 3 and 4 the team jumps between them over floating
 * rocks, and a thrall can't follow.
 */
enum OcellusQuadrant
{
	NE,
	NW,
	SW,
	SE;

	/** Ocellus's arena: the template region of his room. */
	static final int REGION = 16472;
	/**
	 * The arena's centre in template coordinates, where the lines between the
	 * quadrants cross: x 31, y 41 within region 16472. Every jump rock sits on
	 * one of these two lines, symmetric about this point.
	 */
	static final int CENTRE_X = 64 * 64 + 31;
	static final int CENTRE_Y = 88 * 64 + 41;
	/**
	 * Our quadrant counts only this many tiles or more from the centre lines
	 * on both axes, so the rocks between quadrants (and a centre a tile off)
	 * don't count as a quadrant.
	 */
	static final int DEAD_ZONE = 2;

	/** Our quadrant, or null on or near the lines between quadrants. */
	@Nullable
	static OcellusQuadrant of(int x, int y)
	{
		int dx = x - CENTRE_X;
		int dy = y - CENTRE_Y;
		if (Math.abs(dx) < DEAD_ZONE || Math.abs(dy) < DEAD_ZONE)
		{
			return null;
		}
		return nearest(x, y);
	}

	/** The quadrant a point is closest to, with no gap between them (for our thrall, which stays put). */
	static OcellusQuadrant nearest(int x, int y)
	{
		boolean east = x >= CENTRE_X;
		boolean north = y >= CENTRE_Y;
		if (north)
		{
			return east ? NE : NW;
		}
		return east ? SE : SW;
	}
}
