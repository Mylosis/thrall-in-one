package com.thrallinone;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The "Recast Thrall" box: off, or shown steady or pulsing. */
@Getter
@RequiredArgsConstructor
public enum RecastBox
{
	OFF("Off"),
	STEADY("Steady"),
	PULSING("Pulsing");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
