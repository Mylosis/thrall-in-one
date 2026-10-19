package com.thrallinone;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * One fight while it is going on: how long it lasted, how much of that our
 * thrall was out, and the damage dealt by us and by our thrall.
 *
 * <p>A fight runs from our first hit (ours or our thrall's) to our last one,
 * so the wait after the last hit before the fight is called over doesn't
 * count against uptime. Ticks after the latest hit are held back and only
 * counted once another hit shows the fight carried on.
 */
class Fight
{
	static final double SECONDS_PER_TICK = 0.6;

	@Getter
	private int lastHitTick;

	@Getter
	private int ticks;
	@Getter
	private int thrallTicks;
	private final int[] tierTicks = new int[ThrallTier.values().length];
	private int pendingTicks;
	private int pendingThrallTicks;
	private final int[] pendingTierTicks = new int[ThrallTier.values().length];

	/** All our hitsplats on NPCs, the thrall's included (the game shows them as ours). */
	@Getter
	private int totalDamage;
	@Getter
	private int thrallDamage;
	private final Map<ThrallType, Integer> damageByType = new EnumMap<>(ThrallType.class);

	/** Damage (ours and our thrall's) by target, to find who the fight was against. */
	private final Map<Object, Integer> damageByTarget = new HashMap<>();
	private final Map<Object, String> targetNames = new HashMap<>();

	/** The raid this fight is in, if any: raid fights are grouped by raid. */
	@Getter
	@Setter
	@Nullable
	private String raid;

	Fight(int startTick)
	{
		this.lastHitTick = startTick;
	}

	/** Count one game tick, noting whether our thrall was out and its tier (null if unknown). */
	void tick(int tick, boolean thrallOut, @Nullable ThrallTier tier)
	{
		if (tick <= lastHitTick)
		{
			countTick(thrallOut, tier);
		}
		else
		{
			pendingTicks++;
			if (thrallOut)
			{
				pendingThrallTicks++;
				if (tier != null)
				{
					pendingTierTicks[tier.ordinal()]++;
				}
			}
		}
	}

	private void countTick(boolean thrallOut, @Nullable ThrallTier tier)
	{
		ticks++;
		if (thrallOut)
		{
			thrallTicks++;
			if (tier != null)
			{
				tierTicks[tier.ordinal()]++;
			}
		}
	}

	/** One of our hitsplats (ours or our thrall's) on an NPC. */
	void hit(int tick, Object target, String targetName, int amount)
	{
		totalDamage += amount;
		damageByTarget.merge(target, amount, Integer::sum);
		targetNames.put(target, targetName);
		extendTo(tick);
	}

	/** A hitsplat already passed to {@link #hit} that turned out to be our thrall's. */
	void thrallHit(int tick, @Nullable ThrallType type, int amount)
	{
		thrallDamage += amount;
		if (type != null)
		{
			damageByType.merge(type, amount, Integer::sum);
		}
		extendTo(tick);
	}

	private void extendTo(int tick)
	{
		if (tick <= lastHitTick)
		{
			return;
		}
		lastHitTick = tick;
		ticks += pendingTicks;
		thrallTicks += pendingThrallTicks;
		for (int i = 0; i < tierTicks.length; i++)
		{
			tierTicks[i] += pendingTierTicks[i];
			pendingTierTicks[i] = 0;
		}
		pendingTicks = 0;
		pendingThrallTicks = 0;
	}

	/** Game ticks in this many seconds, rounding up. */
	static int secondsToTicks(int seconds)
	{
		return (seconds * 10 + 5) / 6;
	}

	/** Our own damage, without the thrall's. */
	int getOwnDamage()
	{
		return Math.max(0, totalDamage - thrallDamage);
	}

	/** The target that took the most damage, or null before the first hit. */
	@Nullable
	Object getMainTarget()
	{
		Object best = null;
		int bestDamage = -1;
		for (Map.Entry<Object, Integer> entry : damageByTarget.entrySet())
		{
			if (entry.getValue() > bestDamage)
			{
				best = entry.getKey();
				bestDamage = entry.getValue();
			}
		}
		return best;
	}

	String getMainTargetName()
	{
		Object main = getMainTarget();
		return main == null ? "Unknown" : targetNames.get(main);
	}

	/** Where the fight is filed: the raid it was in, or the name of the NPC it was mostly against. */
	String getBoss()
	{
		return raid != null ? raid : getMainTargetName();
	}

	/** Expected DPS for the tiers our thrall had out this fight, weighted by time; 0 if none known. */
	double getExpectedDps()
	{
		int known = 0;
		double sum = 0;
		for (ThrallTier tier : ThrallTier.values())
		{
			known += tierTicks[tier.ordinal()];
			sum += tierTicks[tier.ordinal()] * tier.getExpectedDps();
		}
		return known == 0 ? 0 : sum / known;
	}

	FightRecord toRecord()
	{
		FightRecord record = new FightRecord();
		record.setBoss(getBoss());
		record.setTicks(Math.max(1, ticks));
		record.setThrallTicks(Math.min(thrallTicks, Math.max(1, ticks)));
		record.setThrallDamage(thrallDamage);
		record.setOwnDamage(getOwnDamage());
		record.setExpectedDps(getExpectedDps());
		record.setDamageByType(new EnumMap<>(damageByType));
		return record;
	}
}
