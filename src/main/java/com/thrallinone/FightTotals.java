package com.thrallinone;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Fights added up: for the login session, one boss, or a lifetime. */
@Data
@NoArgsConstructor
class FightTotals
{
	private int fights;
	private long ticks;
	private long thrallTicks;
	private long thrallDamage;
	private long ownDamage;
	/** Sum over fights of the expected DPS times the thrall's time out, for an overall expected DPS. */
	private double expectedDamage;

	FightTotals(FightTotals other)
	{
		fights = other.fights;
		ticks = other.ticks;
		thrallTicks = other.thrallTicks;
		thrallDamage = other.thrallDamage;
		ownDamage = other.ownDamage;
		expectedDamage = other.expectedDamage;
	}

	void add(FightRecord fight)
	{
		fights++;
		ticks += fight.getTicks();
		thrallTicks += fight.getThrallTicks();
		thrallDamage += fight.getThrallDamage();
		ownDamage += fight.getOwnDamage();
		expectedDamage += fight.getExpectedDps() * fight.getThrallTicks() * Fight.SECONDS_PER_TICK;
	}

	double uptime()
	{
		return ticks <= 0 ? 0 : (double) thrallTicks / ticks;
	}

	double share()
	{
		long total = ownDamage + thrallDamage;
		return total <= 0 ? 0 : (double) thrallDamage / total;
	}

	double measuredDps()
	{
		return thrallTicks <= 0 ? 0 : thrallDamage / (thrallTicks * Fight.SECONDS_PER_TICK);
	}

	double expectedDps()
	{
		return thrallTicks <= 0 ? 0 : expectedDamage / (thrallTicks * Fight.SECONDS_PER_TICK);
	}
}
