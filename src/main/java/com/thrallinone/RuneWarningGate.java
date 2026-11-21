package com.thrallinone;

/**
 * Holds the casts warning back while a player is gearing up, so taking the
 * Book of the Dead out before the runes doesn't flash a warning at them (a
 * recorded session showed "No Thrall Runes" for 4 ticks while items were
 * being moved). The warning comes on only once it has
 * been due for more than {@link #GRACE_TICKS} with the bank closed. Going
 * away is immediate, and so is a change between low and out once it is
 * showing. Pure, so it can be unit-tested; {@link ThrallCasts} asks it every
 * game tick.
 */
final class RuneWarningGate
{
	/** 3 seconds. */
	static final int GRACE_TICKS = 5;

	private RuneWarning shown = RuneWarning.NONE;
	/** Ticks in a row the warning has been due and not shown, with the bank closed. */
	private int waited;

	/**
	 * @param due the warning as the runes stand now ({@link RuneWarning#of})
	 * @param bankOpen the bank is open: nothing new comes on, and the wait starts again when it closes
	 * @return the warning to show
	 */
	RuneWarning tick(RuneWarning due, boolean bankOpen)
	{
		if (due == RuneWarning.NONE)
		{
			shown = RuneWarning.NONE;
			waited = 0;
		}
		else if (shown != RuneWarning.NONE)
		{
			// Already warned: follow low and out as they change, bank open or not.
			shown = due;
		}
		else if (bankOpen)
		{
			waited = 0;
		}
		else if (++waited > GRACE_TICKS)
		{
			shown = due;
			waited = 0;
		}
		return shown;
	}

	void reset()
	{
		shown = RuneWarning.NONE;
		waited = 0;
	}
}
