package com.thrallinone;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Keeps track of every thrall in the scene and works out which one belongs to
 * the local player.
 *
 * <p>The main signal is our own cast: the Resurrect cast graphic on our
 * player and the thrall's spawn arrive in the same tick, so the thrall is
 * claimed before it is first drawn, and the resurrection varbit (on about
 * five ticks later) confirms it. If that is missed, the varbit says we have a
 * thrall out but not which NPC it is, and every tick looks for ours (see
 * {@link ThrallClaim} for those rules). The claim is dropped the
 * moment the varbit clears, the NPC despawns, or the NPC is no longer in the
 * scene's NPC list. That last check runs every tick so a stale reference can
 * never keep an outline on screen after a teleport or a death (the
 * lingering-outline bug other thrall plugins have).
 *
 * <p>Losing the claimed thrall with the varbit still on is judged by
 * {@link ThrallLoss}: one left behind in another instance stops counting as
 * out after a short grace (the varbit can stay on long after it), and one
 * that ran out is not replaced by a teammate's. Any claim made without our
 * cast graphic on the spawn tick only takes the type our latest cast raised.
 */
@Slf4j
@Singleton
public class ThrallTracker
{
	/** A jump this far between ticks is a teleport; thralls do not follow teleports. */
	private static final int TELEPORT_DISTANCE = 20;
	/** Spawn ticks given to thralls found by a rescan, so they never count as a fresh cast. */
	private static final int UNKNOWN_SPAWN_TICK = Integer.MIN_VALUE / 2;
	/** The graphic our player shows when casting a Resurrect spell, and the thrall type it raises. */
	private static final Map<Integer, ThrallType> CAST_SPOTANIMS = ImmutableMap.of(
		SpotanimID.RESURRECT_GHOST_CAST_SPOTANIM, ThrallType.MAGIC,
		SpotanimID.RESURRECT_SKELETON_CAST_SPOTANIM, ThrallType.RANGED,
		SpotanimID.RESURRECT_ZOMBIE_CAST_SPOTANIM, ThrallType.MELEE);
	/** A thrall spawning this many ticks after our cast graphic can be ours (measured: same tick). */
	private static final int MY_CAST_TO_SPAWN_TICKS = 1;
	/** The resurrect cooldown is longer than this, so a cast graphic seen again sooner is the same cast. */
	static final int SAME_CAST_TICKS = 10;

	private final Client client;
	/** For the time left when our thrall is lost (the timer depends on this class, hence the provider). */
	private final Provider<ThrallTimer> timer;

	/** Every thrall in the scene, with when and where it appeared. */
	private final Map<NPC, Sighting> thralls = new IdentityHashMap<>();
	/** Thralls we have seen interact with another player: never ours. */
	private final Set<NPC> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Thralls already logged as not claimed for being another type than our latest cast. */
	private final Set<NPC> wrongTypeLogged = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Why our thrall went away with the varbit still on, and whether it's left behind. */
	private final ThrallLoss loss = new ThrallLoss();

	@Getter
	@Nullable
	private NPC owned;
	/** Why the current thrall was claimed, for the debug overlay. */
	@Getter
	private String claimReason = "";

	private boolean active;
	/** Tick the varbit last turned on, or -1; kept after a claim, unlike {@link #castTick}. */
	private int varbitOnTick = -1;
	/** Tick the scene last started loading, or -1. */
	private int loadTick = -1;
	private int castTick = -1;
	/** Tick our player last showed a Resurrect cast graphic, and the thrall type it raises. */
	private int myCastTick = -1;
	@Nullable
	private ThrallType myCastType;
	/**
	 * The last claim made from our cast graphic: the tick it was made, the
	 * cast it came from, and the thrall's spawn tick (relative to that cast)
	 * and distance from us.
	 */
	private int castClaimTick = -1;
	/** Spawn tick of our thrall when it was claimed from our cast, or -1. */
	private int ownedSpawnTick = -1;
	private int castClaimCastTick = -1;
	private int castClaimSpawnOffset;
	private int castClaimDistance;
	/** A claim made at spawn must be confirmed by the varbit by this tick, or it is dropped. */
	private int provisionalUntilTick = -1;
	/** NPC index of our thrall when we lost sight of it while the varbit was still on. */
	private int lostIndex = -1;
	/** We teleported away while our thrall was out; it stayed behind. */
	private boolean stranded;
	private boolean rescanPending;
	/** Read the resurrection varbit on the next tick (the plugin just started). */
	private boolean varbitUnread;
	@Nullable
	private WorldPoint lastPlayerLocation;
	@Nullable
	private WorldPoint locationBeforeLoad;

	@Inject
	ThrallTracker(Client client, Provider<ThrallTimer> timer)
	{
		this.client = client;
		this.timer = timer;
	}

	void start()
	{
		clear();
		// Started mid-game (or during a loading screen): read the varbit and
		// the scene on the first tick, since neither will send an event.
		varbitUnread = true;
		rescanPending = true;
	}

	void stop()
	{
		clear();
	}

	private void clear()
	{
		thralls.clear();
		rejected.clear();
		wrongTypeLogged.clear();
		release("reset");
		loss.reset();
		active = false;
		varbitOnTick = -1;
		loadTick = -1;
		castTick = -1;
		myCastTick = -1;
		myCastType = null;
		castClaimTick = -1;
		castClaimCastTick = -1;
		ownedSpawnTick = -1;
		lostIndex = -1;
		stranded = false;
		rescanPending = false;
		varbitUnread = false;
		lastPlayerLocation = null;
		locationBeforeLoad = null;
	}

	public Set<NPC> getThralls()
	{
		return Collections.unmodifiableSet(thralls.keySet());
	}

	/** This NPC is one of the thralls in the list. Client thread. */
	boolean isListed(NPC npc)
	{
		return thralls.containsKey(npc);
	}

	public boolean isOwned(NPC npc)
	{
		return npc != null && npc == owned;
	}

	/**
	 * Our thrall is out as far as the game's varbit says, unless we left it
	 * behind in another instance (the varbit can stay on long after that).
	 */
	public boolean hasActiveThrall()
	{
		return active && !loss.isLeftBehind();
	}

	/** The resurrection varbit as the game has it, left behind or not. */
	public boolean isVarbitOn()
	{
		return active;
	}

	/** Our thrall stayed in a scene we left and none of ours came back: it doesn't count as out. */
	public boolean isLeftBehind()
	{
		return loss.isLeftBehind();
	}

	/** Tick our thrall began to count as left behind, or -1. */
	public int getLeftBehindTick()
	{
		return loss.getLeftBehindTick();
	}

	/** Why our thrall last went away with the varbit on, until we have one again. */
	public ThrallLoss.Kind getLossKind()
	{
		return loss.getKind();
	}

	/** Tick our thrall last went away with the varbit on, or -1 (as {@link #getLossKind}). */
	public int getLostTick()
	{
		return loss.getLostTick();
	}

	/** Spawn tick of our thrall if it was claimed from our cast (so its start is known), or -1. */
	public int getOwnedSpawnTick()
	{
		return owned == null ? -1 : ownedSpawnTick;
	}

	/** Tick our player last showed a Resurrect cast graphic, or -1. */
	public int getLastCastTick()
	{
		return myCastTick;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (ThrallType.isThrall(npc.getId()))
		{
			thralls.put(npc, sight(npc));
			logSpawn("spawned", npc);
			if (!tryClaimAtSpawn(npc))
			{
				tryClaim();
			}
		}
	}

	/**
	 * Our resurrection varbit turns on about five ticks after the thrall
	 * appears, which would leave it treated as somebody else's until then.
	 * Our own cast graphic comes first, though, so a thrall that appears
	 * beside us just after it is claimed on the spawn, before it is ever
	 * drawn. The varbit then confirms the claim, or it is dropped.
	 */
	private boolean tryClaimAtSpawn(NPC npc)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return false;
		}

		// In case the graphic's event was missed, look at our graphics now.
		noteCastGraphic(me);
		int sinceCast = client.getTickCount() - myCastTick;
		if (myCastTick >= 0 && sinceCast >= 0 && sinceCast <= MY_CAST_TO_SPAWN_TICKS)
		{
			if (claimFromCast(npc, me))
			{
				return true;
			}
			log.debug("Thrall {} spawned {} tick(s) after our cast but wasn't claimed: type {} (cast {}), {} tiles away{}",
				npc.getIndex(), sinceCast, ThrallType.of(npc.getId()), myCastType,
				distanceFromUs(me, npc.getWorldLocation()), npc == owned ? ", already ours" : "");
		}
		else if (myCastTick >= 0 && sinceCast > MY_CAST_TO_SPAWN_TICKS && sinceCast <= SAME_CAST_TICKS)
		{
			// Ours arriving late would show up here.
			log.debug("Thrall {} spawned {} ticks after our cast, too late to claim from it", npc.getIndex(), sinceCast);
		}
		return false;
	}

	/**
	 * Claim a thrall that appeared beside us right after our own cast graphic.
	 * A fresh cast replaces whatever we had, so this does not depend on the
	 * old thrall's varbit clear arriving first. A second matching thrall from
	 * the same cast's window (someone beside us cast the same spell a tick
	 * apart) takes over only if it fits our cast better.
	 */
	private boolean claimFromCast(NPC npc, Player me)
	{
		if (npc == owned)
		{
			return false;
		}
		int distance = distanceFromUs(me, npc.getWorldLocation());
		if (!ThrallClaim.fitsCastAtSpawn(myCastType, ThrallType.of(npc.getId()), distance))
		{
			return false;
		}

		int tick = client.getTickCount();
		Sighting sighting = thralls.get(npc);
		int spawnTick = sighting == null ? tick : sighting.tick;
		int spawnOffset = spawnTick - myCastTick;
		if (owned != null)
		{
			if (castClaimCastTick == myCastTick)
			{
				if (!ThrallClaim.replacesCastClaim(castClaimSpawnOffset, castClaimDistance, spawnOffset, distance))
				{
					return false;
				}
				rejected.add(owned);
				release("a better match for our cast appeared");
			}
			else
			{
				release("replaced by a new cast");
			}
		}
		claim(npc, "your cast");
		ownedSpawnTick = spawnTick;
		castClaimTick = tick;
		castClaimCastTick = myCastTick;
		castClaimSpawnOffset = spawnOffset;
		castClaimDistance = distance;
		// If the varbit is still on from the thrall being replaced, it won't
		// turn on again to confirm this one.
		provisionalUntilTick = active ? -1 : client.getTickCount() + ThrallClaim.CAST_WINDOW_BEFORE_TICKS + 2;
		return true;
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Player me = client.getLocalPlayer();
		if (me == null || event.getActor() != me)
		{
			return;
		}

		if (noteCastGraphic(me))
		{
			// In game the graphic arrived just before the spawn in the same
			// tick; if the order is ever the other way round, the thrall is
			// already here.
			for (Map.Entry<NPC, Sighting> entry : new ArrayList<>(thralls.entrySet()))
			{
				if (entry.getValue().tick == myCastTick && !rejected.contains(entry.getKey()))
				{
					claimFromCast(entry.getKey(), me);
				}
			}
			return;
		}

		if (log.isDebugEnabled())
		{
			List<Integer> ids = new ArrayList<>();
			for (ActorSpotAnim spotAnim : me.getSpotAnims())
			{
				ids.add(spotAnim.getId());
			}
			log.debug("Our spotanims at tick {}: {}", client.getTickCount(), ids);
		}
	}

	/** Record a Resurrect cast graphic on us; true if it is a new cast. */
	private boolean noteCastGraphic(Player me)
	{
		for (Map.Entry<Integer, ThrallType> cast : CAST_SPOTANIMS.entrySet())
		{
			if (me.hasSpotAnim(cast.getKey()))
			{
				int tick = client.getTickCount();
				if (cast.getValue() == myCastType && myCastTick >= 0 && tick - myCastTick < SAME_CAST_TICKS)
				{
					// The same cast's graphic, seen again as another graphic changed
					// on us; moving the cast tick would widen the claim window.
					return false;
				}
				myCastTick = tick;
				myCastType = cast.getValue();
				log.debug("Our resurrect cast graphic {} at tick {}", cast.getKey(), myCastTick);
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player me = client.getLocalPlayer();
		if (me != null && event.getActor() == me && me.getAnimation() != -1 && log.isDebugEnabled())
		{
			log.debug("Our animation {} at tick {}", me.getAnimation(), client.getTickCount());
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC npc = event.getNpc();
		if (ThrallType.isThrall(npc.getId()))
		{
			if (!thralls.containsKey(npc))
			{
				thralls.put(npc, sight(npc));
				logSpawn("changed into a thrall", npc);
			}
			tryClaim();
		}
		else
		{
			forget(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		forget(event.getNpc());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.ARCEUUS_RESURRECTION_ACTIVE)
		{
			return;
		}

		boolean nowActive = event.getValue() != 0;
		log.debug("Resurrection varbit = {} at tick {}", event.getValue(), client.getTickCount());
		if (nowActive == active)
		{
			return;
		}
		active = nowActive;
		stranded = false;
		// Whatever we lost, the varbit now speaks for the thrall out (or none).
		loss.reset();

		if (active)
		{
			castTick = client.getTickCount();
			varbitOnTick = castTick;
			if (owned != null && provisionalUntilTick >= 0)
			{
				provisionalUntilTick = -1;
				claimReason += " (confirmed)";
				log.debug("Varbit confirmed thrall index={}", owned.getIndex());
			}
			tryClaim();
		}
		else
		{
			castTick = -1;
			if (owned != null && castClaimTick == client.getTickCount())
			{
				// The old thrall's varbit cleared in the same tick we claimed
				// its replacement; wait for it to turn on again for the new one.
				provisionalUntilTick = client.getTickCount() + ThrallClaim.CAST_WINDOW_BEFORE_TICKS + 2;
			}
			else
			{
				release("varbit cleared");
			}
			lostIndex = -1;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				// NPC instances may not survive the reload; check them all on the next tick.
				locationBeforeLoad = lastPlayerLocation;
				rescanPending = true;
				loadTick = client.getTickCount();
				loss.sceneChanged(loadTick);
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				clear();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}

		WorldPoint here = me.getWorldLocation();
		boolean teleported = isFarApart(lastPlayerLocation, here);
		lastPlayerLocation = here;

		if (varbitUnread)
		{
			varbitUnread = false;
			active = client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) != 0;
		}
		if (rescanPending)
		{
			rescanPending = false;
			teleported |= isFarApart(locationBeforeLoad, here);
			locationBeforeLoad = null;
			rescan();
		}

		if (teleported && active)
		{
			log.debug("Teleported with a thrall out: it stays behind");
			stranded = true;
		}

		// Despawn events are not guaranteed across every scene change, so
		// drop anything the scene no longer holds rather than outline a ghost.
		thralls.keySet().removeIf(npc -> !isInScene(npc));
		rejected.retainAll(thralls.keySet());
		wrongTypeLogged.retainAll(thralls.keySet());
		if (owned != null && !thralls.containsKey(owned))
		{
			lose("no longer in scene", teleported || isSceneChanging());
		}

		if (owned != null && provisionalUntilTick >= 0 && client.getTickCount() > provisionalUntilTick)
		{
			// Our varbit never turned on: it was somebody else's cast after all.
			rejected.add(owned);
			release("varbit never confirmed");
		}

		if (loss.tick(client.getTickCount()))
		{
			// lostIndex stays: should it turn up after all (slow to catch up), it's
			// claimed again ("same thrall again", type checked) and counts as out.
			log.debug("Thrall left behind at tick {}: lost at tick {} on a scene change and none of ours came back; "
				+ "not out from now, though the varbit is still on", client.getTickCount(), loss.getLostTick());
		}

		correctClaim(me);
		tryClaim();
	}

	/** The scene is loading or just loaded, or we teleported away from our thrall. */
	private boolean isSceneChanging()
	{
		return client.getGameState() == GameState.LOADING || stranded
			|| loadTick >= 0 && client.getTickCount() - loadTick <= 1;
	}

	/** Rebuild the thrall list from the scene and drop anything that is gone. */
	private void rescan()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}

		Map<NPC, Sighting> previous = new IdentityHashMap<>(thralls);
		thralls.clear();
		for (NPC npc : wv.npcs())
		{
			if (npc != null && ThrallType.isThrall(npc.getId()))
			{
				thralls.put(npc, previous.getOrDefault(npc, Sighting.UNKNOWN));
			}
		}
		rejected.retainAll(thralls.keySet());
		wrongTypeLogged.retainAll(thralls.keySet());
		if (owned != null && !thralls.containsKey(owned))
		{
			lose("gone after scene load", true);
		}
	}

	/**
	 * Fix a wrong claim as soon as the game tells us better: a thrall that
	 * interacts with another player is theirs, and one that interacts with us
	 * is ours.
	 */
	private void correctClaim(Player me)
	{
		if (owned == null)
		{
			return;
		}

		Actor target = owned.getInteracting();
		if (target instanceof Player && target != me)
		{
			log.debug("Thrall {} interacts with {}: not ours", owned.getIndex(), target.getName());
			rejected.add(owned);
			release("interacts with another player");
			return;
		}

		if (target != me)
		{
			ThrallType latestCast = latestCastType();
			for (NPC npc : thralls.keySet())
			{
				if (npc != owned && npc.getInteracting() == me && !rejected.contains(npc)
					&& ThrallClaim.fitsLatestCast(latestCast, ThrallType.of(npc.getId())))
				{
					claim(npc, "interacts with us (switched)");
					return;
				}
			}
		}
	}

	private void tryClaim()
	{
		// Our thrall ran out (the varbit clears a tick later): a teammate's
		// beside us isn't its replacement. Only our next cast claims again.
		if (!active || owned != null || thralls.isEmpty() || loss.isExpired())
		{
			return;
		}

		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}

		ThrallType latestCast = latestCastType();
		// The game keeps a thrall's NPC index for as long as it exists, so the
		// one we lost to a scene reload comes back under the same index, even
		// if it is far away by then.
		if (lostIndex >= 0)
		{
			for (NPC npc : thralls.keySet())
			{
				if (npc.getIndex() == lostIndex && !rejected.contains(npc)
					&& ThrallClaim.fitsLatestCast(latestCast, ThrallType.of(npc.getId())))
				{
					claim(npc, "same thrall again");
					return;
				}
			}
		}

		int tick = client.getTickCount();
		ThrallClaim.Mode mode;
		if (castTick >= 0 && tick - castTick <= ThrallClaim.CAST_WINDOW_AFTER_TICKS)
		{
			mode = ThrallClaim.Mode.FRESH_CAST;
		}
		else if (stranded || loss.isLeftBehind())
		{
			mode = ThrallClaim.Mode.INTERACTING_ONLY;
		}
		else
		{
			mode = ThrallClaim.Mode.REACQUIRE;
		}

		WorldPoint myLocation = me.getWorldLocation();
		List<ThrallClaim.Candidate<NPC>> candidates = new ArrayList<>();
		for (Map.Entry<NPC, Sighting> entry : thralls.entrySet())
		{
			NPC npc = entry.getKey();
			if (rejected.contains(npc) || npc.isDead())
			{
				continue;
			}
			Sighting sighting = entry.getValue();
			Actor target = npc.getInteracting();
			int spawnOffset = castTick >= 0 ? sighting.tick - castTick : UNKNOWN_SPAWN_TICK;
			int distance;
			boolean otherPlayerAsClose;
			if (mode == ThrallClaim.Mode.FRESH_CAST && sighting.distance >= 0)
			{
				// By the time the varbit turns on (five ticks after the spawn) a
				// thrall may have run off to attack something, so judge a fresh
				// cast by where it appeared, which is beside its caster.
				distance = sighting.distance;
				otherPlayerAsClose = sighting.otherPlayerAsClose;
			}
			else
			{
				WorldPoint npcLocation = npc.getWorldLocation();
				distance = npcLocation.distanceTo(myLocation);
				otherPlayerAsClose = isOtherPlayerAsClose(me, npc.getWorldView(), npcLocation, distance);
			}
			candidates.add(new ThrallClaim.Candidate<>(
				npc,
				distance,
				target == me,
				target instanceof Player && target != me,
				otherPlayerAsClose,
				spawnOffset,
				ThrallType.of(npc.getId())));
		}

		ThrallClaim.Candidate<NPC> pick = ThrallClaim.pick(candidates, mode, latestCast);
		if (pick != null)
		{
			claim(pick.getThrall(), pick.isInteractingWithMe() ? "interacts with us" : mode.name().toLowerCase().replace('_', ' '));
			return;
		}
		if (!log.isDebugEnabled())
		{
			return;
		}
		if (mode == ThrallClaim.Mode.FRESH_CAST)
		{
			log.debug("Tick {} (cast {}): no thrall claimed from {}", tick, castTick, candidates);
		}
		// Once per thrall: one we would have taken but for its type.
		ThrallClaim.Candidate<NPC> anyType = latestCast == null ? null : ThrallClaim.pick(candidates, mode, null);
		if (anyType != null && wrongTypeLogged.add(anyType.getThrall()))
		{
			log.debug("Not claiming thrall id={} index={} ({}, {} tiles): {}, our latest cast was {}",
				anyType.getThrall().getId(), anyType.getThrall().getIndex(), mode.name().toLowerCase().replace('_', ' '),
				anyType.getDistance(), anyType.getType(), latestCast);
		}
	}

	/** The type of thrall our latest cast raised, or null if unknown ({@link ThrallClaim#latestCastType}). */
	@Nullable
	private ThrallType latestCastType()
	{
		return ThrallClaim.latestCastType(myCastType, myCastTick, varbitOnTick);
	}

	/** When and where a thrall appeared, read the moment we first see it. */
	private Sighting sight(NPC npc)
	{
		int tick = client.getTickCount();
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return new Sighting(tick, -1, false);
		}
		WorldPoint npcLocation = npc.getWorldLocation();
		int distance = distanceFromUs(me, npcLocation);
		return new Sighting(tick, distance, isOtherPlayerAsClose(me, npc.getWorldView(), npcLocation, distance));
	}

	/**
	 * Tiles from us to a thrall that may have just been raised. We can cast
	 * while running, two tiles a tick, so the thrall may rise where we stood
	 * at the start of this tick rather than where we are now.
	 */
	private int distanceFromUs(Player me, WorldPoint location)
	{
		int distance = location.distanceTo(me.getWorldLocation());
		if (lastPlayerLocation != null)
		{
			distance = Math.min(distance, location.distanceTo(lastPlayerLocation));
		}
		return distance;
	}

	private boolean isOtherPlayerAsClose(Player me, @Nullable WorldView wv, WorldPoint npcLocation, int myDistance)
	{
		if (wv == null)
		{
			return false;
		}
		for (Player player : wv.players())
		{
			if (player != null && player != me && player.getWorldLocation().distanceTo(npcLocation) <= myDistance)
			{
				return true;
			}
		}
		return false;
	}

	private void claim(NPC npc, String reason)
	{
		owned = npc;
		claimReason = reason;
		ownedSpawnTick = -1;
		castTick = -1;
		stranded = false;
		provisionalUntilTick = -1;
		lostIndex = -1;
		if (loss.getKind() != ThrallLoss.Kind.NONE)
		{
			log.debug("Thrall lost at tick {} ({}) replaced after {} ticks{}", loss.getLostTick(),
				loss.getKind().name().toLowerCase().replace('_', ' '), client.getTickCount() - loss.getLostTick(),
				loss.isLeftBehind() ? ", after it counted as left behind" : "");
		}
		loss.reset();
		log.debug("Claimed thrall id={} index={} ({})", npc.getId(), npc.getIndex(), reason);
	}

	private void release(String reason)
	{
		if (owned != null)
		{
			log.debug("Released thrall id={} index={} ({})", owned.getId(), owned.getIndex(), reason);
		}
		owned = null;
		claimReason = "";
		ownedSpawnTick = -1;
		provisionalUntilTick = -1;
	}

	/**
	 * Our thrall's NPC went away while it may still be out. Judge why
	 * ({@link ThrallLoss}): unless it ran out, remember it so it can be picked
	 * up again.
	 */
	private void lose(String reason, boolean sceneChange)
	{
		if (owned != null && active)
		{
			// Before the release, while the timer still knows this thrall's start.
			int remaining = timer.get().getRemainingTicks();
			ThrallLoss.Kind kind = loss.lost(client.getTickCount(), sceneChange, remaining);
			lostIndex = kind == ThrallLoss.Kind.EXPIRY ? -1 : owned.getIndex();
			log.debug("Lost our thrall id={} index={} at tick {} with {} ticks left: {}", owned.getId(), owned.getIndex(),
				client.getTickCount(), remaining, kind.name().toLowerCase().replace('_', ' '));
		}
		release(reason);
	}

	private void forget(NPC npc)
	{
		if (thralls.remove(npc) == null)
		{
			return;
		}
		rejected.remove(npc);
		wrongTypeLogged.remove(npc);
		if (npc == owned)
		{
			lose("despawned", isSceneChanging());
		}
	}

	private void logSpawn(String what, NPC npc)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		Player me = client.getLocalPlayer();
		Actor target = npc.getInteracting();
		Sighting sighting = thralls.get(npc);
		log.debug("Thrall {} id={} index={} tick={} castTick={} myCastTick={} varbit={} distance={} otherAsClose={} interacting={}",
			what, npc.getId(), npc.getIndex(), client.getTickCount(), castTick, myCastTick, active,
			sighting == null ? -1 : sighting.distance, sighting != null && sighting.otherPlayerAsClose,
			target == null ? "none" : target == me ? "you" : target.getName());
	}

	private static final class Sighting
	{
		/** For thralls found by a rescan: never a fresh cast, place unknown. */
		static final Sighting UNKNOWN = new Sighting(UNKNOWN_SPAWN_TICK, -1, false);

		final int tick;
		/** Tiles from us when it appeared, or -1 if unknown. */
		final int distance;
		/** Another player stood at least as close to it when it appeared. */
		final boolean otherPlayerAsClose;

		Sighting(int tick, int distance, boolean otherPlayerAsClose)
		{
			this.tick = tick;
			this.distance = distance;
			this.otherPlayerAsClose = otherPlayerAsClose;
		}
	}

	private static boolean isInScene(NPC npc)
	{
		WorldView wv = npc.getWorldView();
		return wv != null
			&& wv.npcs().byIndex(npc.getIndex()) == npc
			&& ThrallType.isThrall(npc.getId());
	}

	private static boolean isFarApart(@Nullable WorldPoint a, WorldPoint b)
	{
		return a != null && a.distanceTo2D(b) > TELEPORT_DISTANCE;
	}
}
