package com.thrallinone;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * The runes the Resurrect spells use, the items that count as them, and the
 * equipment that supplies them for free.
 *
 * <p>Sources: OSRS wiki "Runes" (combination runes act as one of each of
 * their two runes; the aether rune is cosmic + soul), "Staves" (elemental,
 * battle and mystic staves, and the combination battlestaves and mystic
 * staves, are an infinite source of their runes), "Twinflame staff" (fire and
 * water) and "Tome of earth" (charged only; the same holds for the tomes of
 * fire and water). The Kodai wand is an infinite source of water runes. The
 * Book of the Dead supplies no runes.
 */
public enum Rune
{
	AIR,
	WATER,
	EARTH,
	FIRE,
	MIND,
	COSMIC,
	DEATH,
	BLOOD,
	SOUL;

	/**
	 * Rune items, and the runes each one counts as: the one table for both
	 * casts left and the cost of a cast. Plain runes come first, so
	 * {@link #itemsFor} lists a rune before the runes that stand in for it.
	 */
	static final Map<Integer, Set<Rune>> ITEMS = ImmutableMap.<Integer, Set<Rune>>builder()
		.put(ItemID.AIRRUNE, ImmutableSet.of(AIR))
		.put(ItemID.WATERRUNE, ImmutableSet.of(WATER))
		.put(ItemID.EARTHRUNE, ImmutableSet.of(EARTH))
		.put(ItemID.FIRERUNE, ImmutableSet.of(FIRE))
		.put(ItemID.MINDRUNE, ImmutableSet.of(MIND))
		.put(ItemID.COSMICRUNE, ImmutableSet.of(COSMIC))
		.put(ItemID.DEATHRUNE, ImmutableSet.of(DEATH))
		.put(ItemID.BLOODRUNE, ImmutableSet.of(BLOOD))
		.put(ItemID.SOULRUNE, ImmutableSet.of(SOUL))
		// Sunfire runes work as fire runes (OSRS wiki, "Sunfire rune").
		.put(ItemID.SUNFIRERUNE, ImmutableSet.of(FIRE))
		.put(ItemID.MISTRUNE, ImmutableSet.of(AIR, WATER))
		.put(ItemID.DUSTRUNE, ImmutableSet.of(AIR, EARTH))
		.put(ItemID.SMOKERUNE, ImmutableSet.of(AIR, FIRE))
		.put(ItemID.MUDRUNE, ImmutableSet.of(WATER, EARTH))
		.put(ItemID.STEAMRUNE, ImmutableSet.of(WATER, FIRE))
		.put(ItemID.LAVARUNE, ImmutableSet.of(EARTH, FIRE))
		.put(ItemID.AETHERRUNE, ImmutableSet.of(COSMIC, SOUL))
		.build();

	private static final Map<Rune, List<Integer>> ITEMS_FOR = itemsByRune();

	/** The rune items that count as this rune, the plain rune first. */
	static List<Integer> itemsFor(Rune rune)
	{
		return ITEMS_FOR.getOrDefault(rune, Collections.emptyList());
	}

	private static Map<Rune, List<Integer>> itemsByRune()
	{
		Map<Rune, List<Integer>> byRune = new EnumMap<>(Rune.class);
		for (Map.Entry<Integer, Set<Rune>> item : ITEMS.entrySet())
		{
			for (Rune rune : item.getValue())
			{
				byRune.computeIfAbsent(rune, r -> new ArrayList<>()).add(item.getKey());
			}
		}
		byRune.replaceAll((rune, items) -> ImmutableList.copyOf(items));
		return Collections.unmodifiableMap(byRune);
	}

	/** Worn items that supply runes without using any up. */
	static final Map<Integer, Set<Rune>> INFINITE = ImmutableMap.<Integer, Set<Rune>>builder()
		.put(ItemID.STAFF_OF_AIR, ImmutableSet.of(AIR))
		.put(ItemID.AIR_BATTLESTAFF, ImmutableSet.of(AIR))
		.put(ItemID.MYSTIC_AIR_STAFF, ImmutableSet.of(AIR))
		.put(ItemID.STAFF_OF_WATER, ImmutableSet.of(WATER))
		.put(ItemID.WATER_BATTLESTAFF, ImmutableSet.of(WATER))
		.put(ItemID.MYSTIC_WATER_STAFF, ImmutableSet.of(WATER))
		.put(ItemID.STAFF_OF_EARTH, ImmutableSet.of(EARTH))
		.put(ItemID.EARTH_BATTLESTAFF, ImmutableSet.of(EARTH))
		.put(ItemID.MYSTIC_EARTH_STAFF, ImmutableSet.of(EARTH))
		.put(ItemID.STAFF_OF_FIRE, ImmutableSet.of(FIRE))
		.put(ItemID.FIRE_BATTLESTAFF, ImmutableSet.of(FIRE))
		.put(ItemID.MYSTIC_FIRE_STAFF, ImmutableSet.of(FIRE))
		.put(ItemID.MIST_BATTLESTAFF, ImmutableSet.of(AIR, WATER))
		.put(ItemID.MYSTIC_MIST_BATTLESTAFF, ImmutableSet.of(AIR, WATER))
		.put(ItemID.DUST_BATTLESTAFF, ImmutableSet.of(AIR, EARTH))
		.put(ItemID.MYSTIC_DUST_BATTLESTAFF, ImmutableSet.of(AIR, EARTH))
		.put(ItemID.SMOKE_BATTLESTAFF, ImmutableSet.of(AIR, FIRE))
		.put(ItemID.MYSTIC_SMOKE_BATTLESTAFF, ImmutableSet.of(AIR, FIRE))
		.put(ItemID.MUD_BATTLESTAFF, ImmutableSet.of(WATER, EARTH))
		.put(ItemID.MYSTIC_MUD_STAFF, ImmutableSet.of(WATER, EARTH))
		.put(ItemID.STEAM_BATTLESTAFF, ImmutableSet.of(WATER, FIRE))
		.put(ItemID.MYSTIC_STEAM_BATTLESTAFF, ImmutableSet.of(WATER, FIRE))
		.put(ItemID.STEAM_BATTLESTAFF_PRETTY, ImmutableSet.of(WATER, FIRE))
		.put(ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY, ImmutableSet.of(WATER, FIRE))
		.put(ItemID.TWINFLAME_STAFF, ImmutableSet.of(WATER, FIRE))
		.put(ItemID.LAVA_BATTLESTAFF, ImmutableSet.of(EARTH, FIRE))
		.put(ItemID.MYSTIC_LAVA_STAFF, ImmutableSet.of(EARTH, FIRE))
		.put(ItemID.LAVA_BATTLESTAFF_PRETTY, ImmutableSet.of(EARTH, FIRE))
		.put(ItemID.MYSTIC_LAVA_STAFF_PRETTY, ImmutableSet.of(EARTH, FIRE))
		.put(ItemID.KODAI_WAND, ImmutableSet.of(WATER))
		.put(ItemID.TOME_OF_WATER, ImmutableSet.of(WATER))
		.put(ItemID.TOME_OF_EARTH, ImmutableSet.of(EARTH))
		.put(ItemID.TOME_OF_FIRE, ImmutableSet.of(FIRE))
		.build();
}
