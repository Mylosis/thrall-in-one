package com.thrallinone;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * How many Resurrect spells of a tier the runes you carry pay for. Pure, so
 * it can be unit-tested; {@link ThrallCasts} gathers the items from the client.
 *
 * <p>A combination rune counts towards each of its two runes. That is exact
 * here because no rune item counts as two of the runes one thrall spell needs
 * (each tier needs a single elemental rune, and the aether rune's soul half
 * is never needed).
 */
public final class CastsCounter
{
	private CastsCounter()
	{
	}

	/**
	 * @param carried item ID to quantity, from the inventory and, if a rune pouch is in the inventory, its contents
	 * @param worn the item IDs of worn equipment, for staves and tomes that supply runes
	 * @return the number of casts the runes pay for
	 */
	public static int castsLeft(ThrallTier tier, Map<Integer, Integer> carried, Collection<Integer> worn)
	{
		Set<Rune> free = EnumSet.noneOf(Rune.class);
		for (int itemId : worn)
		{
			Set<Rune> runes = Rune.INFINITE.get(itemId);
			if (runes != null)
			{
				free.addAll(runes);
			}
		}

		Map<Rune, Long> have = new EnumMap<>(Rune.class);
		for (Map.Entry<Integer, Integer> item : carried.entrySet())
		{
			Set<Rune> runes = Rune.ITEMS.get(item.getKey());
			if (runes != null && item.getValue() > 0)
			{
				for (Rune rune : runes)
				{
					have.merge(rune, (long) item.getValue(), Long::sum);
				}
			}
		}

		long casts = Integer.MAX_VALUE;
		for (Map.Entry<Rune, Integer> cost : tier.getRunes().entrySet())
		{
			if (!free.contains(cost.getKey()))
			{
				casts = Math.min(casts, have.getOrDefault(cost.getKey(), 0L) / cost.getValue());
			}
		}
		return (int) casts;
	}
}
