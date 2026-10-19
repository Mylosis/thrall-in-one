package com.thrallinone;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.api.gameval.NpcID;

/**
 * Places where you can fight but can't summon a thrall, so reminders there
 * would only nag. The OSRS wiki ("Thrall", where thralls cannot be
 * resurrected) lists them; the ones matched exactly are below. Everywhere else
 * the plugin learns a spot from the game's own chat line when a cast fails.
 *
 * <p>Region ids are template regions (WorldPoint.fromLocalInstance), so they
 * hold inside instances. Sailing is matched by the player standing on a boat's
 * WorldView instead of a region.
 */
final class NoSummonAreas
{
	/** Nightmare Zone dreams. RuneLite core's Nightmare Zone and Discord plugins use the same region. */
	static final int NIGHTMARE_ZONE_REGION = 9033;
	/**
	 * Temple of the Eye (Guardians of the Rift). The wiki places it at 3613, 9501,
	 * which is region 14484; RuneLite core's Discord plugin uses the same id.
	 */
	static final int GUARDIANS_OF_THE_RIFT_REGION = 14484;
	/** Player-owned house instance templates, as listed in RuneLite core's Discord plugin. */
	static final Set<Integer> POH_REGIONS = ImmutableSet.of(7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303);

	private static final Set<Integer> FIXED_REGIONS = ImmutableSet.<Integer>builder()
		.add(NIGHTMARE_ZONE_REGION)
		.add(GUARDIANS_OF_THE_RIFT_REGION)
		.addAll(POH_REGIONS)
		.build();

	/**
	 * Quest bosses fought in instances where thralls can't be summoned. Matched by
	 * NPC so we don't need each instance's region. Fight forms only, plus the
	 * forms each boss switches to mid-fight.
	 */
	private static final Set<Integer> QUEST_BOSSES = ImmutableSet.of(
		// Galvek (Dragon Slayer II)
		NpcID.GALVEK_WAVES, NpcID.GALVEK_FIRE, NpcID.GALVEK_WATER, NpcID.GALVEK_WIND,
		NpcID.GALVEK_EARTH, NpcID.GALVEK_TSUNAMI,
		// Robert the Strong (Dragon Slayer II)
		NpcID.DREAM_ROBERT_COMBAT,
		// Vanstrom Klause (Sins of the Father)
		NpcID.MYQ5_VANSTROM_COMBAT, NpcID.MYQ5_VANSTROM_COMBAT_NOOP, NpcID.MYQ5_VANSTROM_COMBAT_ENRAGE,
		// Fragment of Seren (Song of the Elves)
		NpcID.DARK_SEREN, NpcID.DARK_SEREN_PHANTOM,
		// Lowerniel Drakan (The Blood Moon Rises)
		NpcID.MYQ6_LOWERNIEL_FINAL_COMBAT
	);

	/**
	 * The game's line when a thrall can't be resurrected where you stand. Not yet
	 * captured in game; two wordings are reported: "You can't resurrect a thrall
	 * here." (Chasm of Fire) and "The ground is too soft to resurrect a thrall
	 * here." (Jormungand's Prison). Both say "resurrect", "thrall" and "here", so
	 * match on those.
	 */
	static final Pattern CANT_SUMMON_MESSAGE = Pattern.compile("\\bresurrect\\b.*\\bthralls?\\b.*\\bhere\\b",
		Pattern.CASE_INSENSITIVE);

	/** Game messages worth logging while the wording above is unconfirmed. */
	private static final Pattern THRALL_MESSAGE = Pattern.compile("thrall|resurrect", Pattern.CASE_INSENSITIVE);

	private NoSummonAreas()
	{
	}

	static boolean isFixedRegion(int regionId)
	{
		return FIXED_REGIONS.contains(regionId);
	}

	static boolean isQuestBoss(int npcId)
	{
		return QUEST_BOSSES.contains(npcId);
	}

	static boolean isCantSummonMessage(String message)
	{
		return message != null && CANT_SUMMON_MESSAGE.matcher(message).find();
	}

	static boolean mentionsThralls(String message)
	{
		return message != null && THRALL_MESSAGE.matcher(message).find();
	}

	/** Learned regions as saved in the profile: comma-separated ids. Anything unreadable is skipped. */
	static Set<Integer> parseRegions(String saved)
	{
		Set<Integer> regions = new TreeSet<>();
		if (saved == null)
		{
			return regions;
		}
		for (String part : saved.split(","))
		{
			try
			{
				regions.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException e)
			{
				// Skip it.
			}
		}
		return regions;
	}

	static String formatRegions(Collection<Integer> regions)
	{
		return regions.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
	}
}
