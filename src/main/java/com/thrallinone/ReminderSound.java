package com.thrallinone;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.SoundEffectID;

/** Recast Sound: off, or one of the game's own sounds. */
@Getter
@RequiredArgsConstructor
public enum ReminderSound
{
	OFF("Off", -1),
	BELL("Bell", SoundEffectID.TOWN_CRIER_RING_BELL_UP),
	CHIME("Chime", SoundEffectID.ALTAR_CRYSTAL_CHIME_1),
	PING("Ping", SoundEffectID.SNP_WARNING_PING);

	private final String name;
	/** The game's sound id, or -1 for Off. */
	private final int soundId;

	@Override
	public String toString()
	{
		return name;
	}
}
