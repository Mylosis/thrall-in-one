package com.thrallinone;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * Time left on our thrall. A thrall lasts one tick per boosted Magic level,
 * twice that once the Master combat achievement rewards are claimed (OSRS
 * wiki, "Thrall"). Measured in game, the resurrection varbit turns on about
 * five ticks after the cast and clears one tick short of the level in ticks
 * later. The countdown runs from the spawn when the tracker claimed the
 * thrall from our cast, then from the varbit, which lands on the same expiry.
 * The varbit clearing ends it exactly, whatever the estimate said.
 *
 * <p>When the plugin starts with a thrall already out, the varbit change was
 * missed. So each countdown is saved to the RuneLite profile as its expiry on
 * the map clock (a server tick counter, varp 3079) plus the thrall's NPC
 * index, and resumes if a thrall with that index is still out. (The buff
 * bar's client vars for the thrall, varc 524 and 1147, looked like a source
 * but stayed 0 in game even with the buff bar and its thrall timer on.)
 */
@Slf4j
@Singleton
public class ThrallTimer
{
	private static final int CA_TIER_CLAIMED = 2;
	private static final String SAVED_END_KEY = "thrallEnd";
	/** Longer than any thrall can last: twice a boosted 99 Magic, with room to spare. */
	private static final int MAX_TICKS = 250;
	/** Measured in game: the varbit turns on this many ticks after our thrall spawns. */
	private static final int SPAWN_TO_VARBIT_TICKS = 5;

	private final Client client;
	private final ThrallTracker tracker;
	private final ConfigManager configManager;

	private boolean active;
	private int activeTick = -1;
	private int endTick = -1;

	@Inject
	ThrallTimer(Client client, ThrallTracker tracker, ConfigManager configManager)
	{
		this.client = client;
		this.tracker = tracker;
		this.configManager = configManager;
	}

	void reset()
	{
		active = client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) != 0;
		activeTick = -1;
		endTick = -1;
		if (!active)
		{
			return;
		}

		// Started part way through a thrall.
		int remaining = remainingFromSaved();
		if (remaining >= 0)
		{
			endTick = client.getTickCount() + remaining;
		}
		log.debug("Thrall out at start-up: {} ticks left from the saved expiry (saved {}, map clock {})",
			remaining, savedEnd(), mapClock());
	}

	/** Ticks left from the saved expiry, or -1 unless the thrall it was saved for is still out. */
	private int remainingFromSaved()
	{
		String saved = savedEnd();
		if (saved == null)
		{
			return -1;
		}

		int endMapClock;
		int npcIndex;
		try
		{
			String[] parts = saved.split(":");
			endMapClock = Integer.parseInt(parts[0]);
			npcIndex = Integer.parseInt(parts[1]);
		}
		catch (RuntimeException e)
		{
			return -1;
		}

		WorldView wv = client.getTopLevelWorldView();
		NPC npc = wv == null ? null : wv.npcs().byIndex(npcIndex);
		if (npc == null || !ThrallType.isThrall(npc.getId()))
		{
			// A different thrall, cast while the plugin was off.
			return -1;
		}
		return remainingUntil(endMapClock, mapClock());
	}

	/** Ticks from now to an expiry on the map clock, or -1 when it isn't a plausible thrall expiry. */
	static int remainingUntil(int endMapClock, int mapClock)
	{
		int remaining = endMapClock - mapClock;
		return remaining > 0 && remaining <= MAX_TICKS ? remaining : -1;
	}

	private String savedEnd()
	{
		return configManager.getRSProfileConfiguration(ThrallInOneConfig.GROUP, SAVED_END_KEY);
	}

	private int mapClock()
	{
		return client.getVarpValue(VarPlayerID.MAP_CLOCK);
	}

	/** Ticks until our thrall expires, or -1 when there is no thrall, it was left behind, or its start was missed. */
	public int getRemainingTicks()
	{
		if (tracker.isLeftBehind())
		{
			// The varbit may stay on long after (see ThrallLoss); nothing of ours is counting down.
			return -1;
		}
		int end = endTick;
		// Claimed from our cast on the spawn tick, before the varbit (or with
		// the varbit still on from the thrall it replaced): count from the spawn.
		int spawnTick = tracker.getOwnedSpawnTick();
		if (spawnTick >= 0 && spawnTick > activeTick)
		{
			end = spawnTick + SPAWN_TO_VARBIT_TICKS + durationTicks() - 1;
		}
		if (end < 0)
		{
			return -1;
		}
		return Math.max(0, end - client.getTickCount());
	}

	/** How long a thrall cast now lasts, in ticks after the varbit turns on, plus one. */
	private int durationTicks()
	{
		boolean doubled = client.getVarbitValue(VarbitID.CA_TIER_STATUS_MASTER) == CA_TIER_CLAIMED;
		return client.getBoostedSkillLevel(Skill.MAGIC) * (doubled ? 2 : 1);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.ARCEUUS_RESURRECTION_ACTIVE)
		{
			return;
		}

		boolean nowActive = event.getValue() != 0;
		if (nowActive == active)
		{
			return;
		}
		active = nowActive;

		int tick = client.getTickCount();
		if (active)
		{
			activeTick = tick;
			endTick = tick + durationTicks() - 1;

			NPC owned = tracker.getOwned();
			if (owned != null)
			{
				configManager.setRSProfileConfiguration(ThrallInOneConfig.GROUP, SAVED_END_KEY,
					(mapClock() + endTick - tick) + ":" + owned.getIndex());
			}
			log.debug("Thrall timer started at tick {} ({} ticks after the spawn): expect expiry at tick {}; saved for index {}",
				tick, tracker.getOwnedSpawnTick() < 0 ? "?" : tick - tracker.getOwnedSpawnTick(), endTick,
				owned == null ? "-" : owned.getIndex());
		}
		else
		{
			if (activeTick >= 0)
			{
				log.debug("Thrall lasted {} ticks after the varbit, predicted {}", tick - activeTick, endTick - activeTick);
			}
			activeTick = -1;
			endTick = -1;
			configManager.unsetRSProfileConfiguration(ThrallInOneConfig.GROUP, SAVED_END_KEY);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				active = false;
				activeTick = -1;
				endTick = -1;
				break;
			default:
				break;
		}
	}

	/**
	 * Ticks left as whole seconds, rounded to the nearest, so a full cast at
	 * 99 Magic (99 ticks, 59.4 s) starts at 59 and the last tick shows 1.
	 */
	static int seconds(int ticks)
	{
		return (ticks * 6 + 5) / 10;
	}

	/** "59s": seconds only, as the timer shows them. */
	static String format(int ticks)
	{
		return seconds(ticks) + "s";
	}
}
