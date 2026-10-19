package com.thrallinone;

import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The three thrall combat styles. Every thrall NPC (Arceuus base, the Leagues
 * imp cosmetics and the Deadman cosmetics) maps to exactly one of these, in
 * {@link ThrallNpcs}.
 */
@Getter
@RequiredArgsConstructor
public enum ThrallType
{
	// Players see a thrall's type as its combat style, everywhere.
	MAGIC("Mage"),
	RANGED("Ranged"),
	MELEE("Melee");

	private final String displayName;

	/** The type of a thrall NPC ({@link ThrallNpcs}), or null for any other NPC. */
	@Nullable
	public static ThrallType of(int npcId)
	{
		return ThrallNpcs.type(npcId);
	}

	public static boolean isThrall(int npcId)
	{
		return ThrallNpcs.isThrall(npcId);
	}
}
