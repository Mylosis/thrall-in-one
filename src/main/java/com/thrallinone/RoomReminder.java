package com.thrallinone;

import javax.annotation.Nullable;
import lombok.Getter;

/**
 * A boss of The Fractured Archive in progress, from "Challenge started:
 * Bork." to "Challenge complete: Bork.". While it's on and we haven't cast
 * since it started, {@link TfaHelper} checks whether our thrall was left
 * outside the arena. Ticks are passed in, so the rules can be unit tested
 * without the client.
 */
final class RoomReminder
{
	private static final String STARTED = "Challenge started: ";
	private static final String COMPLETE = "Challenge complete: ";

	/** Tick the last boss started, or -1 once it's done. */
	@Getter
	private int startTick = -1;

	/** The boss named by a "Challenge started" game message (tags removed), or null for any other message. */
	@Nullable
	static String boss(String message)
	{
		if (!message.startsWith(STARTED))
		{
			return null;
		}
		String boss = message.substring(STARTED.length()).trim();
		if (boss.endsWith("."))
		{
			boss = boss.substring(0, boss.length() - 1);
		}
		return boss.isEmpty() ? null : boss;
	}

	/** A "Challenge complete" game message (tags removed). */
	static boolean complete(String message)
	{
		return message.startsWith(COMPLETE);
	}

	void started(int tick)
	{
		startTick = tick;
	}

	/** A boss is in progress and we haven't cast since it started: the only time our thrall can be left outside. */
	boolean active(int tick, int lastCastTick)
	{
		return startTick >= 0 && tick >= startTick && lastCastTick < startTick;
	}

	void reset()
	{
		startTick = -1;
	}
}
