package com.thrallinone;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How our own thrall is drawn. In the order players pick them most. */
@Getter
@RequiredArgsConstructor
public enum OwnThrallShow
{
	OUTLINE_AND_MODEL("Outline + Model", true, true),
	OUTLINE_ONLY("Outline Only", true, false),
	NORMAL("Normal", false, true),
	HIDDEN("Hidden", false, false);

	private final String name;
	private final boolean outline;
	private final boolean model;

	/** The look the old "hide" and "outline" ticks made together. */
	static OwnThrallShow of(boolean outline, boolean model)
	{
		for (OwnThrallShow show : values())
		{
			if (show.outline == outline && show.model == model)
			{
				return show;
			}
		}
		throw new AssertionError();
	}

	@Override
	public String toString()
	{
		return name;
	}
}
