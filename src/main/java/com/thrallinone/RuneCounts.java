package com.thrallinone;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/**
 * Rune counts in the inventory plus the rune pouch, and the runes a thrall
 * cast took. Staves, tomes and combination runes change what actually leaves
 * the inventory, so the cost is measured rather than taken from the spell:
 * whatever the cast's runes (or runes that stand in for them, from
 * {@link Rune#ITEMS}) dropped by.
 */
final class RuneCounts
{
	private RuneCounts()
	{
	}

	/**
	 * Every item in the inventory plus the runes in the rune pouch, by item
	 * id. The pouch counts even while it's in the bank: only the drop across
	 * a cast matters here, and a banked pouch doesn't change.
	 */
	static Map<Integer, Integer> read(Client client)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() >= 0 && item.getQuantity() > 0)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		RunePouch.addContents(client, counts);
		return counts;
	}

	/**
	 * The runes a cast of this tier took: the drop in each rune the spell
	 * uses (or one that stands in for it), never more than the spell needs,
	 * so another spell cast around the same tick isn't counted.
	 */
	static Map<Integer, Integer> usedByCast(ThrallTier tier, Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		Map<Integer, Integer> used = new HashMap<>();
		for (Map.Entry<Rune, Integer> rune : tier.getRunes().entrySet())
		{
			int left = rune.getValue();
			for (int item : Rune.itemsFor(rune.getKey()))
			{
				// Less whatever an earlier rune of this cast already took from it.
				int drop = before.getOrDefault(item, 0) - after.getOrDefault(item, 0) - used.getOrDefault(item, 0);
				int taken = Math.min(drop, left);
				if (taken > 0)
				{
					used.merge(item, taken, Integer::sum);
					left -= taken;
				}
			}
		}
		return used;
	}
}
