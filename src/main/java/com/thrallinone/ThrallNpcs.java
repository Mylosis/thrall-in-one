package com.thrallinone;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.gameval.NpcID;

/**
 * Every thrall NPC, with its type and tier, in one table: the Arceuus base
 * thralls, the Leagues imp cosmetics and the Deadman cosmetics (greater
 * thralls).
 */
final class ThrallNpcs
{
	private static final Map<Integer, ThrallType> TYPES;
	private static final Map<Integer, ThrallTier> TIERS;
	/** The id range the table covers, to skip most NPCs without boxing their id (checked every frame). */
	private static final int MIN_ID;
	private static final int MAX_ID;

	static
	{
		Table table = new Table()
			.add(NpcID.ARCEUUS_THRALL_GHOST_LESSER, ThrallType.MAGIC, ThrallTier.LESSER)
			.add(NpcID.ARCEUUS_THRALL_GHOST_SUPERIOR, ThrallType.MAGIC, ThrallTier.SUPERIOR)
			.add(NpcID.ARCEUUS_THRALL_GHOST_GREATER, ThrallType.MAGIC, ThrallTier.GREATER)
			.add(NpcID.THRALL_IMP_MAGIC_LESSER, ThrallType.MAGIC, ThrallTier.LESSER)
			.add(NpcID.THRALL_IMP_MAGIC_SUPERIOR, ThrallType.MAGIC, ThrallTier.SUPERIOR)
			.add(NpcID.THRALL_IMP_MAGIC_GREATER, ThrallType.MAGIC, ThrallTier.GREATER)
			.add(NpcID.DEADMAN_THRALL_GHOSTLY_GREATER_WISP, ThrallType.MAGIC, ThrallTier.GREATER)

			.add(NpcID.ARCEUUS_THRALL_SKELETON_LESSER, ThrallType.RANGED, ThrallTier.LESSER)
			.add(NpcID.ARCEUUS_THRALL_SKELETON_SUPERIOR, ThrallType.RANGED, ThrallTier.SUPERIOR)
			.add(NpcID.ARCEUUS_THRALL_SKELETON_GREATER, ThrallType.RANGED, ThrallTier.GREATER)
			.add(NpcID.THRALL_IMP_RANGED_LESSER, ThrallType.RANGED, ThrallTier.LESSER)
			.add(NpcID.THRALL_IMP_RANGED_SUPERIOR, ThrallType.RANGED, ThrallTier.SUPERIOR)
			.add(NpcID.THRALL_IMP_RANGED_GREATER, ThrallType.RANGED, ThrallTier.GREATER)
			.add(NpcID.DEADMAN_THRALL_SKELETAL_GREATER_PRINCESS, ThrallType.RANGED, ThrallTier.GREATER)

			.add(NpcID.ARCEUUS_THRALL_ZOMBIE_LESSER, ThrallType.MELEE, ThrallTier.LESSER)
			.add(NpcID.ARCEUUS_THRALL_ZOMBIE_SUPERIOR, ThrallType.MELEE, ThrallTier.SUPERIOR)
			.add(NpcID.ARCEUUS_THRALL_ZOMBIE_GREATER, ThrallType.MELEE, ThrallTier.GREATER)
			.add(NpcID.THRALL_IMP_MELEE_LESSER, ThrallType.MELEE, ThrallTier.LESSER)
			.add(NpcID.THRALL_IMP_MELEE_SUPERIOR, ThrallType.MELEE, ThrallTier.SUPERIOR)
			.add(NpcID.THRALL_IMP_MELEE_GREATER, ThrallType.MELEE, ThrallTier.GREATER)
			.add(NpcID.DEADMAN_THRALL_ZOMBIE_GREATER_ZUK, ThrallType.MELEE, ThrallTier.GREATER);
		TYPES = table.types.build();
		TIERS = table.tiers.build();
		MIN_ID = table.min;
		MAX_ID = table.max;
	}

	private ThrallNpcs()
	{
	}

	static boolean isThrall(int npcId)
	{
		return npcId >= MIN_ID && npcId <= MAX_ID && TYPES.containsKey(npcId);
	}

	@Nullable
	static ThrallType type(int npcId)
	{
		return isThrall(npcId) ? TYPES.get(npcId) : null;
	}

	@Nullable
	static ThrallTier tier(int npcId)
	{
		return isThrall(npcId) ? TIERS.get(npcId) : null;
	}

	private static final class Table
	{
		final ImmutableMap.Builder<Integer, ThrallType> types = ImmutableMap.builder();
		final ImmutableMap.Builder<Integer, ThrallTier> tiers = ImmutableMap.builder();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;

		Table add(int npcId, ThrallType type, ThrallTier tier)
		{
			types.put(npcId, type);
			tiers.put(npcId, tier);
			min = Math.min(min, npcId);
			max = Math.max(max, npcId);
			return this;
		}
	}
}
