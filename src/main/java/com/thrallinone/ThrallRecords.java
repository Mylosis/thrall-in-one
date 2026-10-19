package com.thrallinone;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * Lifetime thrall records for one account, saved as JSON in the RuneLite
 * profile: totals over every saved fight, the same per raid, and summons,
 * prayer and rune cost, added as each thrall is cast.
 */
@Data
class ThrallRecords
{
	private int version = 2;

	private FightTotals lifetime = new FightTotals();
	private Map<ThrallType, Long> damageByType = new LinkedHashMap<>();
	/** Per raid, keyed by {@link BossKey}'s raid name. */
	private Map<String, BossRecord> bosses = new LinkedHashMap<>();

	private int summons;
	private Map<ThrallType, Integer> summonsByType = new LinkedHashMap<>();
	private long prayerSpent;
	/** What the runes that left the inventory or rune pouch on our casts were worth on the day. */
	private long gpSpent;

	@Data
	static class BossRecord
	{
		private FightTotals totals = new FightTotals();
	}

	void addFight(FightRecord fight)
	{
		lifetime.add(fight);
		fight.getDamageByType().forEach((type, damage) -> damageByType.merge(type, (long) damage, Long::sum));
		if (BossKey.isRaid(fight.getBoss()))
		{
			bosses.computeIfAbsent(fight.getBoss(), name -> new BossRecord()).totals.add(fight);
		}
	}

	/**
	 * One cast of ours. The type and tier are null when the thrall it raised
	 * wasn't seen; then only the count and cost go up.
	 */
	void addSummon(ThrallType type, ThrallTier tier, long gp)
	{
		summons++;
		if (type != null)
		{
			summonsByType.merge(type, 1, Integer::sum);
		}
		if (tier != null)
		{
			prayerSpent += tier.getPrayerCost();
		}
		gpSpent += gp;
	}

	/** Fill in anything a damaged save left out, and drop entries that aren't raids. */
	ThrallRecords repair()
	{
		if (lifetime == null)
		{
			lifetime = new FightTotals();
		}
		if (damageByType == null)
		{
			damageByType = new LinkedHashMap<>();
		}
		if (bosses == null)
		{
			bosses = new LinkedHashMap<>();
		}
		bosses.entrySet().removeIf(boss -> boss.getValue() == null || boss.getValue().totals == null
			|| !BossKey.isRaid(boss.getKey()));
		if (summonsByType == null)
		{
			summonsByType = new LinkedHashMap<>();
		}
		// An enum name that doesn't exist reads back as a null key.
		damageByType.remove(null);
		summonsByType.remove(null);
		version = 2;
		return this;
	}
}
