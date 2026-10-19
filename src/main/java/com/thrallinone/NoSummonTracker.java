package com.thrallinone;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

/**
 * Whether we stand somewhere a thrall can't be summoned: a place from
 * {@link NoSummonAreas}, on a boat, in a quest boss fight, or a region where
 * the game told us so. Learned regions are saved per account in the RuneLite
 * profile, and dropped again if a thrall is summoned there after all.
 */
@Slf4j
@Singleton
public class NoSummonTracker
{
	private static final String LEARNED_KEY = "noSummonRegions";

	private final Client client;
	private final ConfigManager configManager;

	private final Set<Integer> learned = new HashSet<>();
	private final Set<NPC> questBosses = new HashSet<>();
	private int regionId = -1;
	private boolean onBoat;
	private boolean noSummonHere;

	@Inject
	NoSummonTracker(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	void reset()
	{
		loadLearned();
		questBosses.clear();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (NoSummonAreas.isQuestBoss(npc.getId()))
				{
					questBosses.add(npc);
				}
			}
		}
		update();
	}

	/** Updated once a tick, before the reminders read it. */
	public boolean isNoSummonHere()
	{
		return noSummonHere;
	}

	@Subscribe(priority = 1)
	public void onGameTick(GameTick event)
	{
		update();
	}

	private void update()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			regionId = -1;
			onBoat = false;
			noSummonHere = false;
			return;
		}

		WorldView view = me.getWorldView();
		boolean nowOnBoat = view != null && !view.isTopLevel();
		LocalPoint local = me.getLocalLocation();
		WorldPoint where = local == null ? null : WorldPoint.fromLocalInstance(client, local);
		int nowRegion = where == null ? -1 : where.getRegionID();

		boolean now = nowOnBoat
			|| !questBosses.isEmpty()
			|| NoSummonAreas.isFixedRegion(nowRegion)
			|| learned.contains(nowRegion);
		if (now != noSummonHere || nowOnBoat != onBoat)
		{
			log.debug("Can't summon here: {} (region {}, on a boat {}, quest bosses {}, learned {})",
				now, nowRegion, nowOnBoat, questBosses.size(), learned.contains(nowRegion));
		}
		regionId = nowRegion;
		onBoat = nowOnBoat;
		noSummonHere = now;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM && type != ChatMessageType.ENGINE)
		{
			return;
		}
		String message = event.getMessage();
		if (!NoSummonAreas.mentionsThralls(message))
		{
			return;
		}
		// Logged so the exact wording of the can't-summon line can be captured in game.
		log.debug("Thrall game message ({}) in region {}: {}", type, regionId, message);

		if (NoSummonAreas.isCantSummonMessage(message) && regionId >= 0 && !onBoat
			&& !NoSummonAreas.isFixedRegion(regionId) && learned.add(regionId))
		{
			log.debug("Learned no-summon region {}", regionId);
			saveLearned();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// A thrall came out here, so this isn't a no-summon spot after all.
		if (event.getVarbitId() == VarbitID.ARCEUUS_RESURRECTION_ACTIVE && event.getValue() != 0
			&& regionId >= 0 && learned.remove(regionId))
		{
			log.debug("Summoned in learned no-summon region {}; forgetting it", regionId);
			saveLearned();
			update();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (NoSummonAreas.isQuestBoss(npc.getId()))
		{
			questBosses.add(npc);
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC npc = event.getNpc();
		if (NoSummonAreas.isQuestBoss(npc.getId()))
		{
			questBosses.add(npc);
		}
		else
		{
			questBosses.remove(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		questBosses.remove(event.getNpc());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			questBosses.clear();
			update();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadLearned();
	}

	private void loadLearned()
	{
		learned.clear();
		learned.addAll(NoSummonAreas.parseRegions(
			configManager.getRSProfileConfiguration(ThrallInOneConfig.GROUP, LEARNED_KEY)));
		if (!learned.isEmpty())
		{
			log.debug("Loaded learned no-summon regions {}", learned);
		}
	}

	private void saveLearned()
	{
		if (learned.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(ThrallInOneConfig.GROUP, LEARNED_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(ThrallInOneConfig.GROUP, LEARNED_KEY,
				NoSummonAreas.formatRegions(learned));
		}
	}
}
