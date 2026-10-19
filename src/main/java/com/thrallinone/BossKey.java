package com.thrallinone;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.annotation.Nullable;

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

	private static final Set<String> RAIDS = ImmutableSet.of(COX, TOB, TOA);

	private BossKey()
	{
	}

	/** Whether a saved boss key is one of the raids (shown in its own section). */
	static boolean isRaid(String boss)
	{
		return RAIDS.contains(boss);
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
		return null;
	}
}
