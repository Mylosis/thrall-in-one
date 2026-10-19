package com.thrallinone;

import java.util.Locale;

/** How fight numbers read in the sidebar panel. */
final class StatsFormat
{
	private StatsFormat()
	{
	}

	/** 0.0832 reads "8.3%"; whole percentages from 10% up drop the decimal ("96%"). */
	static String percent(double fraction)
	{
		double percent = fraction * 100;
		if (percent >= 10 || percent == 0)
		{
			return Math.round(percent) + "%";
		}
		return String.format(Locale.ROOT, "%.1f%%", percent);
	}

	static String dps(double dps)
	{
		return String.format(Locale.ROOT, "%.3f", dps);
	}

	/** Game ticks as minutes and seconds, "1:05". */
	static String duration(long ticks)
	{
		long seconds = Math.round(ticks * Fight.SECONDS_PER_TICK);
		return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
	}

	/** Game ticks as hours and minutes from an hour up ("98h 12m"), otherwise as {@link #duration}. */
	static String longDuration(long ticks)
	{
		long minutes = Math.round(ticks * Fight.SECONDS_PER_TICK) / 60;
		return minutes < 60 ? duration(ticks) : minutes / 60 + "h " + minutes % 60 + "m";
	}

	static String number(long value)
	{
		return String.format(Locale.ROOT, "%,d", value);
	}

	/**
	 * Short enough for a stat tile: in full up to 10,000 ("6,204"), then
	 * thousands, millions or billions with one decimal, cut rather than
	 * rounded as the game does ("214.8K", "7.3M").
	 */
	static String shortNumber(long value)
	{
		if (value < 10_000)
		{
			return number(value);
		}
		if (value < 1_000_000)
		{
			return tenths(value / 100) + "K";
		}
		if (value < 1_000_000_000)
		{
			return tenths(value / 100_000) + "M";
		}
		return tenths(value / 100_000_000) + "B";
	}

	private static String tenths(long tenths)
	{
		return tenths / 10 + "." + tenths % 10;
	}

	static String plural(long count, String one, String many)
	{
		return number(count) + " " + (count == 1 ? one : many);
	}
}
