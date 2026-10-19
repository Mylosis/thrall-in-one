package com.thrallinone;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How other players' thralls are drawn. The same looks as
 * {@link OwnThrallShow}, in the order players pick them most for others.
 */
@Getter
@RequiredArgsConstructor
public enum OthersThrallShow
{
	HIDDEN("Hidden", false, false),
	OUTLINE_ONLY("Outline Only", true, false),
	NORMAL("Normal", false, true),
	OUTLINE_AND_MODEL("Outline + Model", true, true);

	private final String name;
	private final boolean outline;
	private final boolean model;

	/** The look the old "hide" and "outline" ticks made together. */
	static OthersThrallShow of(boolean outline, boolean model)
	{
		for (OthersThrallShow show : values())
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
