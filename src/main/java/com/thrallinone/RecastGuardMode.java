package com.thrallinone;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Recast Guard: off, dim the Resurrect spells, or dim them and ignore left-clicks on them. */
@Getter
@RequiredArgsConstructor
public enum RecastGuardMode
{
	OFF("Off", false),
	DIM("Dim Only", false),
	DIM_AND_BLOCK("Dim + Block", true);

	private final String name;
	/** Left-clicks on the spells are ignored while the guard is up. */
	private final boolean blocking;

	@Override
	public String toString()
	{
		return name;
	}
}
