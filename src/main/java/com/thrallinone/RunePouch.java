package com.thrallinone;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * The rune pouch: which items are pouches, and its contents, which the game
 * keeps in varbits (set even while the pouch is in the bank). Every slot the
 * game has a varbit for is read; empty slots read 0.
 */
final class RunePouch
{
	/** The rune pouches; their contents only count as carried while one is in the inventory. */
	static final Set<Integer> ITEMS = ImmutableSet.of(
		ItemID.BH_RUNE_POUCH,
		ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH,
		ItemID.DIVINE_RUNE_POUCH_TROUVER);

	private static final int[] TYPES = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
	};
	private static final int[] QUANTITIES = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
	};

	private RunePouch()
	{
	}

	/** The varbit holds a pouch slot's rune or quantity. */
	static boolean isPouchVarbit(int varbitId)
	{
		for (int i = 0; i < TYPES.length; i++)
		{
			if (TYPES[i] == varbitId || QUANTITIES[i] == varbitId)
			{
				return true;
			}
		}
		return false;
	}

	/** Add the pouch's runes to the counts, by item id. */
	static void addContents(Client client, Map<Integer, Integer> counts)
	{
		EnumComposition runeIds = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < TYPES.length; i++)
		{
			int type = client.getVarbitValue(TYPES[i]);
			int quantity = client.getVarbitValue(QUANTITIES[i]);
			if (type > 0 && quantity > 0)
			{
				int item = runeIds.getIntValue(type);
				if (item > 0)
				{
					counts.merge(item, quantity, Integer::sum);
				}
			}
		}
	}
}
