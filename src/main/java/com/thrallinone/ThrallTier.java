package com.thrallinone;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * The three thrall tiers: the runes and prayer one Resurrect spell of that
 * tier costs, and how hard the thrall hits. Ghostly, skeletal and zombified
 * thralls of a tier cost the same.
 *
 * <p>Source: OSRS wiki, "Thrall" (https://oldschool.runescape.wiki/w/Thrall):
 * <ul>
 * <li>Resurrect Lesser Ghost / Skeleton / Zombie: level 38, 10 air, 1 cosmic, 5 mind.</li>
 * <li>Resurrect Superior Ghost / Skeleton / Zombie: level 57, 10 earth, 1 cosmic, 5 death.</li>
 * <li>Resurrect Greater Ghost / Skeleton / Zombie: level 76, 10 fire, 1 cosmic, 5 blood.</li>
 * </ul>
 * Every cast also needs the Book of the Dead, worn or in the inventory, and
 * costs prayer points. A thrall hits 0 to its max every 4 ticks without
 * missing, so its expected DPS is the average hit over 2.4 seconds.
 */
@Getter
public enum ThrallTier
{
	LESSER("Lesser", ImmutableMap.of(Rune.AIR, 10, Rune.COSMIC, 1, Rune.MIND, 5), 1, 2),
	SUPERIOR("Superior", ImmutableMap.of(Rune.EARTH, 10, Rune.COSMIC, 1, Rune.DEATH, 5), 2, 4),
	GREATER("Greater", ImmutableMap.of(Rune.FIRE, 10, Rune.COSMIC, 1, Rune.BLOOD, 5), 3, 6);

	private static final double ATTACK_SECONDS = 2.4;

	private final String displayName;
	/** Runes one cast uses. */
	private final Map<Rune, Integer> runes;
	private final int maxHit;
	private final int prayerCost;

	ThrallTier(String displayName, Map<Rune, Integer> runes, int maxHit, int prayerCost)
	{
		this.displayName = displayName;
		this.runes = runes;
		this.maxHit = maxHit;
		this.prayerCost = prayerCost;
	}

	/** Average damage per second: thralls always hit, uniformly 0 to the max. */
	public double getExpectedDps()
	{
		return maxHit / 2.0 / ATTACK_SECONDS;
	}

	/** The tier of a thrall NPC ({@link ThrallNpcs}), or null for any other NPC. */
	@Nullable
	public static ThrallTier of(int npcId)
	{
		return ThrallNpcs.tier(npcId);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
