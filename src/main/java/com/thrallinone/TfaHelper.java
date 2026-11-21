package com.thrallinone;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Where The Fractured Archive leaves our thrall behind without a scene
 * change, which {@link ThrallLoss} can't see. A stranded thrall counts as
 * gone for {@link ThrallReminders} (so the usual Recast Thrall box and its
 * settings apply) and for {@link RecastGuard}. Always on; no settings of its own.
 *
 * <p>Quadrants: in Ocellus's phases 3 and 4 the team jumps between the
 * arena's corner quadrants and a thrall can't follow. Once we land in a
 * quadrant with ours left in another one, it's stranded until we cast or it
 * turns up beside us. It never looks at what Ocellus does (Plugin Hub rule on
 * boss-mechanic indicators): only where we and our thrall stand, and his
 * phase-3 NPC to know the quadrants are in play.
 *
 * <p>Rooms: a boss starting ("Challenge started: …") counts as the start of a
 * fight for the reminders ({@link #getBossStartTick}), so a thrall that ran
 * out between rooms is reminded before the first hit. A thrall of ours that
 * can't walk to us (cast before the lectern, which moves us into the arena
 * without a scene change, and left outside it) is stranded until we cast, it
 * reaches us or goes, or the boss is done.
 */
@Slf4j
@Singleton
public class TfaHelper
{
	/**
	 * A thrall this close counts as with us even across a quadrant line: a
	 * stranded one waits 14-18 tiles away, a following one a tile or two
	 * behind.
	 */
	private static final int NEAR_TILES = 4;

	private final Client client;
	private final ThrallTracker tracker;

	private final QuadrantWatch quadrants = new QuadrantWatch();
	private final RoomReminder room = new RoomReminder();

	/** Ocellus in phase 3 or 4, while he's in the scene. */
	@Nullable
	private NPC ocellus;
	private boolean scanPending;
	/** Our quadrant this tick, or null outside phases 3 and 4 or near the lines between quadrants. */
	@Getter
	@Nullable
	private OcellusQuadrant quadrant;
	/** Our thrall's quadrant this tick, or null if none of ours is out (phases 3 and 4 only). */
	@Getter
	@Nullable
	private OcellusQuadrant thrallQuadrant;
	/** A boss is on, we haven't cast since it started, and our thrall is out but can't walk to us. */
	@Getter
	private boolean roomStranded;

	@Inject
	TfaHelper(Client client, ThrallTracker tracker)
	{
		this.client = client;
		this.tracker = tracker;
	}

	void reset()
	{
		quadrants.reset();
		room.reset();
		ocellus = null;
		scanPending = true;
		quadrant = null;
		thrallQuadrant = null;
		roomStranded = false;
	}

	/**
	 * Our thrall is stranded, in another Ocellus quadrant or outside a boss
	 * arena it can't walk into: it doesn't count as out, for the reminders and
	 * Recast Guard. The caller checks the thrall is still out at all.
	 */
	public boolean isStranded()
	{
		return quadrants.isStranded() || roomStranded;
	}

	/** Tick the TFA boss in progress started on, or -1 with none. */
	public int getBossStartTick()
	{
		return room.getStartTick();
	}

	// After the tracker's own tick handler (priority 0), so our thrall is this tick's.
	@Subscribe(priority = -1)
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		if (scanPending)
		{
			scanPending = false;
			findOcellus();
		}

		Player me = client.getLocalPlayer();
		NPC owned = tracker.getOwned();
		boolean thrallOut = tracker.hasActiveThrall() && owned != null;
		updateQuadrants(tick, me, thrallOut ? owned : null);

		updateRoom(tick, me, thrallOut ? owned : null);
	}

	private void updateRoom(int tick, @Nullable Player me, @Nullable NPC thrall)
	{
		int lastCast = tracker.getLastCastTick();
		if (room.getStartTick() >= 0 && !BossKey.TFA.equals(BossKey.current(client)))
		{
			// Left the raid mid-boss: no "Challenge complete" is coming.
			room.reset();
		}
		if (!room.active(tick, lastCast))
		{
			roomStranded = false;
			return;
		}

		boolean strandedBefore = roomStranded;
		roomStranded = thrall != null && !canReach(thrall, me);
		if (roomStranded != strandedBefore)
		{
			log.debug("Rooms: our thrall {} us at tick {}", roomStranded ? "can't reach" : "can reach", tick);
		}
	}

	/**
	 * Whether our thrall can walk to us over the collision map: false when a
	 * wall or rubble shuts it out, as when the lectern moves us into a boss
	 * arena and it stays outside. True when unsure (no player, no map).
	 */
	private boolean canReach(NPC thrall, @Nullable Player me)
	{
		LocalPoint from = thrall.getLocalLocation();
		LocalPoint to = me == null ? null : me.getLocalLocation();
		WorldView wv = client.getTopLevelWorldView();
		if (from == null || to == null || wv == null)
		{
			return true;
		}
		int fx = from.getSceneX();
		int fy = from.getSceneY();
		int tx = to.getSceneX();
		int ty = to.getSceneY();
		if (Math.max(Math.abs(fx - tx), Math.abs(fy - ty)) <= NEAR_TILES)
		{
			return true;
		}
		if (thrall.getWorldLocation().getPlane() != me.getWorldLocation().getPlane())
		{
			return false;
		}
		CollisionData[] maps = wv.getCollisionMaps();
		int plane = wv.getPlane();
		if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			return true;
		}
		return Reach.reachable(maps[plane].getFlags(), fx, fy, tx, ty);
	}

	private void updateQuadrants(int tick, @Nullable Player me, @Nullable NPC thrall)
	{
		WorldPoint at = me == null ? null : template(me.getLocalLocation());
		if (ocellus == null || at == null || at.getRegionID() != OcellusQuadrant.REGION)
		{
			if (quadrants.getOurs() != null)
			{
				log.debug("Quadrants: out of phases 3 and 4 at tick {}", tick);
			}
			quadrants.reset();
			quadrant = null;
			thrallQuadrant = null;
			return;
		}

		quadrant = OcellusQuadrant.of(at.getX(), at.getY());
		WorldPoint thrallAt = thrall == null ? null : template(thrall.getLocalLocation());
		if (thrallAt == null)
		{
			thrallQuadrant = null;
		}
		else if (at.distanceTo2D(thrallAt) <= NEAR_TILES)
		{
			// Right beside us it's with us, whichever side of a line it stands.
			thrallQuadrant = quadrant != null ? quadrant : quadrants.getOurs();
		}
		else
		{
			thrallQuadrant = OcellusQuadrant.nearest(thrallAt.getX(), thrallAt.getY());
		}

		OcellusQuadrant before = quadrants.getOurs();
		boolean wasStranded = quadrants.isStranded();
		if (quadrants.tick(tick, quadrant, thrallQuadrant))
		{
			log.debug("Quadrants: landed in {} at tick {} with our thrall in {}: stranded", quadrant, tick, thrallQuadrant);
		}
		else if (quadrant != null && quadrant != before)
		{
			log.debug("Quadrants: landed in {} at tick {} with our thrall", quadrant, tick);
		}
		quadrants.cast(tracker.getLastCastTick());
		if (wasStranded && !quadrants.isStranded())
		{
			log.debug("Quadrants: reminder ended at tick {} (cast at tick {}, thrall in {})",
				tick, tracker.getLastCastTick(), thrallQuadrant);
		}
	}

	@Nullable
	private WorldPoint template(@Nullable LocalPoint local)
	{
		return local == null ? null : WorldPoint.fromLocalInstance(client, local);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		String boss = RoomReminder.boss(message);
		if (boss != null && BossKey.TFA.equals(BossKey.current(client)))
		{
			room.started(client.getTickCount());
			log.debug("Rooms: {} started at tick {} (thrall out: {})", boss, client.getTickCount(),
				tracker.hasActiveThrall() && tracker.getOwned() != null);
		}
		else if (RoomReminder.complete(message))
		{
			room.reset();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (isOcellusLate(event.getNpc()))
		{
			ocellus = event.getNpc();
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC npc = event.getNpc();
		if (isOcellusLate(npc))
		{
			ocellus = npc;
		}
		else if (npc == ocellus)
		{
			ocellus = null;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc() == ocellus)
		{
			ocellus = null;
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			quadrants.reset();
			room.reset();
		}
		else if (event.getActor() == ocellus)
		{
			ocellus = null;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
				reset();
				scanPending = false;
				break;
			default:
				break;
		}
	}

	/** Started mid-fight: Ocellus's spawn already went by. */
	private void findOcellus()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		for (NPC npc : wv.npcs())
		{
			if (isOcellusLate(npc))
			{
				ocellus = npc;
				return;
			}
		}
	}

	/** Ocellus in phase 3 (and 4, the same NPC): the quadrants are in play. */
	private static boolean isOcellusLate(@Nullable NPC npc)
	{
		return npc != null && npc.getId() == NpcID.TFA_OCELLUS_P3;
	}
}
