package com.thrallinone;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * Raid fights are filed under the raid rather than the NPC: a raid room has
 * many NPCs, and players compare whole raids. Raids are told apart by the map
 * region of the room's template (raids are instanced).
 */
final class BossKey
{
	static final String COX = "Chambers of Xeric";
	static final String TOB = "Theatre of Blood";
	static final String TOA = "Tombs of Amascut";
	static final String TFA = "The Fractured Archive";

	/** Maiden, Bloat, Nylocas, Sotetseg (and its maze), Xarpus, Verzik. */
	private static final Set<Integer> TOB_REGIONS = ImmutableSet.of(12613, 13125, 13122, 13123, 13379, 12612, 12611);
	/** Nexus, the four paths (puzzle room and boss each), the Wardens and the tomb. */
	private static final Set<Integer> TOA_REGIONS = ImmutableSet.of(
		14160,
		14674, 14676,
		15698, 15700,
		14162, 14164,
		15186, 15188,
		15184, 15696,
		14672);
	/** The nexus (with the Archive Guardian), Ocellus, Ezekiel, the Dagon'hai, Moia, Xarth, Bork and Dreadnoth. */
	private static final Set<Integer> TFA_REGIONS = ImmutableSet.of(
		16471, 16472,
		16214, 16215, 16216,
		16726, 16727, 16728);

	private static final Set<String> RAIDS = ImmutableSet.of(COX, TOB, TOA, TFA);

	private BossKey()
	{
	}

	/** Whether a saved boss key is one of the raids (shown in its own section). */
	static boolean isRaid(String boss)
	{
		return RAIDS.contains(boss);
	}

	/** The raid we are standing in, or null. Client thread. */
	@Nullable
	static String current(Client client)
	{
		boolean inChambers = client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
		return raid(inChambers, templateRegion(client));
	}

	/** The region we stand in, as the map has it outside instances; -1 if unknown. Client thread. */
	static int templateRegion(Client client)
	{
		Player me = client.getLocalPlayer();
		WorldPoint location = me == null ? null : WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		return location == null ? -1 : location.getRegionID();
	}

	/** The raid we are in, from the Chambers of Xeric varbit and the template region, or null. */
	@Nullable
	static String raid(boolean inChambers, int templateRegion)
	{
		if (inChambers)
		{
			return COX;
		}
		if (TOB_REGIONS.contains(templateRegion))
		{
			return TOB;
		}
		if (TOA_REGIONS.contains(templateRegion))
		{
			return TOA;
		}
		if (TFA_REGIONS.contains(templateRegion))
		{
			return TFA;
		}
		return null;
	}
}
