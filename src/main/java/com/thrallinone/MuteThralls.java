package com.thrallinone;

import lombok.RequiredArgsConstructor;

/** Which thralls' sounds are silenced. */
@RequiredArgsConstructor
public enum MuteThralls
{
	OFF("Off"),
	OTHERS("Other Thralls"),
	ALL("All Thralls");

	private final String name;

	/** Whether a sound made by a thrall is silenced, given whether that thrall is ours. */
	boolean mutes(boolean owned)
	{
		return this == ALL || (this == OTHERS && !owned);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
