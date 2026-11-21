package com.thrallinone;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tells other players' thrall projectiles from our thrall's, so they can be
 * hidden with their thralls. Thrall projectiles don't carry their source NPC
 * (as Pet Pathing and Combat found, and our own logs show), so each one is
 * judged once, when it is fired: a thrall projectile that doesn't start on our
 * thrall's tile is someone else's.
 */
@Slf4j
@Singleton
public class ThrallProjectiles
{
	private final ThrallTracker tracker;

	/** Thrall projectiles already judged. */
	private final Set<Projectile> seen = Collections.newSetFromMap(new IdentityHashMap<>());
	/** The ones fired by other players' thralls. */
	private final Set<Projectile> others = Collections.newSetFromMap(new IdentityHashMap<>());

	@Inject
	ThrallProjectiles(ThrallTracker tracker)
	{
		this.tracker = tracker;
	}

	/**
	 * Projectiles fired by ghostly and skeletal thralls, including the Leagues
	 * imp cosmetics. Read for every projectile in the scene, so a switch: no
	 * boxing of the id.
	 */
	static boolean isThrallProjectile(int id)
	{
		switch (id)
		{
			case SpotanimID.THRALL_MAGIC_TRAVEL:
			case SpotanimID.THRALL_RANGED_TRAVEL:
			case SpotanimID.LEAGUES_6_THRALL_IMP_MAGIC_ATTACK_VFX_PROJANIM:
			case SpotanimID.LEAGUES_6_THRALL_IMP_RANGED_ATTACK_VFX_PROJANIM:
				return true;
			default:
				return false;
		}
	}

	/** The projectile was fired from the thrall's tile. */
	static boolean startsAt(Projectile projectile, NPC thrall)
	{
		LocalPoint location = thrall.getLocalLocation();
		return location != null
			&& Math.abs(projectile.getX1() - location.getX()) <= Perspective.LOCAL_TILE_SIZE / 2
			&& Math.abs(projectile.getY1() - location.getY()) <= Perspective.LOCAL_TILE_SIZE / 2;
	}

	/** Fired by another player's thrall. Read for every drawn projectile, so a set lookup only. */
	boolean isOthers(Projectile projectile)
	{
		return others.contains(projectile);
	}

	void reset()
	{
		seen.clear();
		others.clear();
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		if (!isThrallProjectile(projectile.getId()) || !seen.add(projectile))
		{
			return;
		}
		NPC owned = tracker.getOwned();
		if (owned == null || !startsAt(projectile, owned))
		{
			others.add(projectile);
			// With no thrall claimed, a projectile from ours would wrongly count as someone else's.
			log.debug("Thrall projectile {} judged someone else's ({})", projectile.getId(),
				owned == null ? "no thrall claimed" : "not from our thrall's tile");
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		seen.removeIf(projectile -> projectile.getRemainingCycles() <= 0);
		others.retainAll(seen);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
			case LOADING:
				reset();
				break;
			default:
				break;
		}
	}
}
