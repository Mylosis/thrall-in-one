package com.thrallinone;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Which thralls' outlines take their color from the thrall's type (Mage, Ranged, Melee). */
@Getter
@RequiredArgsConstructor
public enum ColorByType
{
	OFF("Off", false, false),
	YOURS("Yours", true, false),
	OTHERS("Others", false, true),
	BOTH("Both", true, true);

	private final String name;
	/** Your thrall uses Your Type Colors. */
	private final boolean yours;
	/** Other players' thralls use Others' Type Colors. */
	private final boolean others;

	@Override
	public String toString()
	{
		return name;
	}
}
