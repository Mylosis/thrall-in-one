package com.thrallinone;

import java.util.Arrays;
import net.runelite.api.CollisionDataFlag;

/**
 * Whether one scene tile can walk to another over the game's collision map:
 * walls, rubble and other blocking objects stop it. Used by {@link TfaHelper}
 * to tell a thrall left outside a boss arena (the lectern moves you in without
 * a scene change, and the thrall can't follow) from one standing back in the
 * fight. Cardinal steps only: the game allows a diagonal step only where both
 * of its cardinal steps are free, so diagonals reach no extra tile.
 */
final class Reach
{
	private static final int FULL = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

	// Reused between calls, as this runs every tick in a TFA boss fight: a tile counts as seen when its
	// stamp equals this call's generation, so nothing needs clearing. Client thread only.
	private static int[] stamps = new int[0];
	private static int[] queue = new int[0];
	private static int generation;

	private Reach()
	{
	}

	/**
	 * @param flags one plane's collision flags, [x][y] in scene coordinates
	 * @return whether a 1-tile walker at (sx, sy) can reach (tx, ty); false if either is outside the map
	 */
	static boolean reachable(int[][] flags, int sx, int sy, int tx, int ty)
	{
		int w = flags.length;
		int h = w == 0 ? 0 : flags[0].length;
		if (!inside(sx, sy, w, h) || !inside(tx, ty, w, h))
		{
			return false;
		}
		if (sx == tx && sy == ty)
		{
			return true;
		}
		int size = w * h;
		if (stamps.length < size)
		{
			stamps = new int[size];
			queue = new int[size];
			generation = 0;
		}
		if (++generation == Integer.MAX_VALUE)
		{
			Arrays.fill(stamps, 0);
			generation = 1;
		}
		int gen = generation;
		int[] seen = stamps;
		int[] cells = queue;

		int head = 0;
		int tail = 0;
		cells[tail++] = sx * h + sy;
		seen[sx * h + sy] = gen;
		while (head < tail)
		{
			int cell = cells[head++];
			int x = cell / h;
			int y = cell % h;
			// East, west, north, south: the destination mustn't be blocked, nor walled on the side we enter from.
			tail = step(flags, seen, gen, cells, tail, x + 1, y, CollisionDataFlag.BLOCK_MOVEMENT_WEST, w, h);
			tail = step(flags, seen, gen, cells, tail, x - 1, y, CollisionDataFlag.BLOCK_MOVEMENT_EAST, w, h);
			tail = step(flags, seen, gen, cells, tail, x, y + 1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, w, h);
			tail = step(flags, seen, gen, cells, tail, x, y - 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, w, h);
			if (seen[tx * h + ty] == gen)
			{
				return true;
			}
		}
		return false;
	}

	private static int step(int[][] flags, int[] seen, int gen, int[] queue, int tail, int x, int y, int side, int w, int h)
	{
		if (!inside(x, y, w, h) || seen[x * h + y] == gen || (flags[x][y] & (FULL | side)) != 0)
		{
			return tail;
		}
		seen[x * h + y] = gen;
		queue[tail] = x * h + y;
		return tail + 1;
	}

	private static boolean inside(int x, int y, int w, int h)
	{
		return x >= 0 && y >= 0 && x < w && y < h;
	}
}
