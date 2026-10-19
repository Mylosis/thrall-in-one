package com.thrallinone;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/** A finished fight: the facts the session and the saved totals are added up from. Not saved itself. */
@Data
class FightRecord
{
	/** Where the fight is filed: the NPC it was mostly against, or the raid. */
	private String boss;
	private int ticks;
	/** Ticks of the fight with our thrall out. */
	private int thrallTicks;
	private int thrallDamage;
	/** Our own damage, without the thrall's. */
	private int ownDamage;
	/** The wiki's DPS for the thrall tiers we had out, weighted by time. */
	private double expectedDps;
	private Map<ThrallType, Integer> damageByType = new LinkedHashMap<>();
}
