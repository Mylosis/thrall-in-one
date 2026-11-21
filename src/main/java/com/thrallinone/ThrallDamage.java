package com.thrallinone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.eventbus.Subscribe;

/**
 * Picks out the damage our thrall deals and hands each hit to
 * {@link ThrallFights}, which splits it into fights.
 *
 * <p>The game shows our thrall's hitsplats as ours (the same "mine" type as
 * our own hits), so they can't be told apart by type. Instead every attack
 * our thrall makes is noted when it starts: the projectile it fires (ghostly
 * and skeletal thralls) or its attack animation (zombified thralls). Each
 * attack expects one hitsplat of 0 to 3 (thralls always hit, max 3) on its
 * target within a tick of when it should land, and the first such hitsplat
 * is counted as the thrall's. If one of our own hits of 3 or less lands on
 * the same target in the same tick, the count can be off by that hit.
 */
@Slf4j
@Singleton
public class ThrallDamage
{
	private static final int THRALL_MAX_HIT = 3;
	/** Client cycles per game tick. */
	private static final int CYCLES_PER_TICK = 30;

	private final Client client;
	private final ThrallTracker tracker;
	private final ThrallFights fights;

	private final List<ExpectedHit> expected = new ArrayList<>();
	/** Small hitsplats of ours this tick that no thrall attack has claimed (yet). */
	private final List<SeenHitsplat> unclaimed = new ArrayList<>();
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());

	/** The last NPC we attacked; our thrall keeps attacking it after we stop. */
	@Nullable
	private Actor lastPlayerTarget;
	/** The thrall whose animations we last saw, and the tick we first saw it. */
	private NPC lastThrall;
	private int lastThrallSince;

	@Inject
	ThrallDamage(Client client, ThrallTracker tracker, ThrallFights fights)
	{
		this.client = client;
		this.tracker = tracker;
		this.fights = fights;
	}

	void reset()
	{
		expected.clear();
		unclaimed.clear();
		seenProjectiles.clear();
		lastThrall = null;
		lastPlayerTarget = null;
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		NPC thrall = tracker.getOwned();
		// Thrall projectiles don't carry their source NPC, so a projectile is
		// tied to our thrall by starting on its tile.
		if (thrall == null || seenProjectiles.contains(projectile) || !ThrallProjectiles.startsAt(projectile, thrall))
		{
			return;
		}
		seenProjectiles.add(projectile);

		if (projectile.getSourceActor() != thrall && !ThrallProjectiles.isThrallProjectile(projectile.getId()))
		{
			// Something else fired from our thrall's tile (us standing on it,
			// or a cosmetic thrall projectile we don't know yet).
			log.debug("Projectile {} started at our thrall but isn't a known thrall projectile (source {})",
				projectile.getId(), projectile.getSourceActor() == null ? "none" : projectile.getSourceActor().getName());
			return;
		}

		// Measured in game: the hitsplat reaches the client in the tick the
		// bolt is fired (it is only drawn when the bolt lands), or a tick
		// later from further away.
		Actor target = targetOf(thrall, projectile.getTargetActor());
		int tick = client.getTickCount();
		int ticksToLand = (projectile.getRemainingCycles() + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK;
		log.debug("Thrall projectile {} at {} from tick {}, lands about tick {}",
			projectile.getId(), nameOf(target), tick, tick + ticksToLand);
		expect(target, ThrallType.of(thrall.getId()), tick, tick + ticksToLand + 1);
	}

	/**
	 * Who our thrall is attacking. Thralls attack whatever we attack, and a
	 * zombified thrall never reports a target, so fall back to our own target,
	 * then the last NPC we attacked (the thrall keeps going after we stop).
	 * Null means unknown: the attack then matches a hitsplat on any NPC.
	 */
	@Nullable
	private Actor targetOf(NPC thrall, @Nullable Actor reported)
	{
		if (reported != null)
		{
			return reported;
		}
		if (thrall.getInteracting() != null)
		{
			return thrall.getInteracting();
		}
		Player me = client.getLocalPlayer();
		if (me != null && me.getInteracting() instanceof NPC)
		{
			return me.getInteracting();
		}
		return lastPlayerTarget;
	}

	private static String nameOf(@Nullable Actor actor)
	{
		return actor == null ? "an unknown NPC" : actor.getName();
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		NPC thrall = tracker.getOwned();
		if (thrall == null || event.getActor() != thrall || thrall.getAnimation() == -1)
		{
			return;
		}

		int tick = client.getTickCount();
		if (thrall != lastThrall)
		{
			lastThrall = thrall;
			lastThrallSince = tick;
		}
		// Its first animation is rising from the ground, not an attack.
		if (tick == lastThrallSince || ThrallType.of(thrall.getId()) != ThrallType.MELEE)
		{
			return;
		}

		// Measured in game: a zombified thrall attacks every 4 ticks without
		// reporting a target, and the hitsplat follows a tick later.
		Actor target = targetOf(thrall, null);
		log.debug("Thrall attack animation {} at {} on tick {}", thrall.getAnimation(), nameOf(target), tick);
		// Never the same tick: a hitsplat arriving with the animation is our own hit.
		expect(target, ThrallType.MELEE, tick + 1, tick + 2);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Hitsplat hitsplat = event.getHitsplat();
		Actor target = event.getActor();
		int tick = client.getTickCount();
		if (log.isDebugEnabled() && expected.stream().anyMatch(hit -> hit.target == null || hit.target == target))
		{
			log.debug("Hitsplat {} (type {}, mine {}) on {} at tick {}",
				hitsplat.getAmount(), hitsplat.getHitsplatType(), hitsplat.isMine(), target.getName(), tick);
		}
		// Unnamed NPCs are hidden helpers (ThrallFights.isNamed): their hitsplats are bookkeeping, and
		// matching one would use up the expected hit that belongs on the real target.
		if (!hitsplat.isMine() || hitsplat.getAmount() > THRALL_MAX_HIT || target == client.getLocalPlayer()
			|| !ThrallFights.isNamed(target.getName()))
		{
			return;
		}

		for (Iterator<ExpectedHit> it = expected.iterator(); it.hasNext(); )
		{
			ExpectedHit hit = it.next();
			if ((hit.target == null || hit.target == target) && tick >= hit.fromTick && tick <= hit.toTick)
			{
				it.remove();
				record(hitsplat.getAmount(), target, tick, hit.type);
				return;
			}
		}

		// The thrall's attack may show up later in this same tick; keep the
		// hitsplat until the tick ends so that attack can still claim it.
		if (tracker.getOwned() != null)
		{
			unclaimed.add(new SeenHitsplat(target, hitsplat.getAmount(), tick));
		}
	}

	/**
	 * Note an attack by our thrall, or count it straight away if its hitsplat already arrived this tick.
	 * The thrall's type is taken now: it may run out before the hit lands.
	 */
	private void expect(@Nullable Actor target, @Nullable ThrallType type, int fromTick, int toTick)
	{
		for (Iterator<SeenHitsplat> it = unclaimed.iterator(); it.hasNext(); )
		{
			SeenHitsplat seen = it.next();
			if ((target == null || seen.target == target) && seen.tick >= fromTick)
			{
				it.remove();
				record(seen.amount, seen.target, seen.tick, type);
				return;
			}
		}
		expected.add(new ExpectedHit(target, type, fromTick, toTick));
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player me = client.getLocalPlayer();
		if (me != null && me.getInteracting() instanceof NPC)
		{
			lastPlayerTarget = me.getInteracting();
		}
		else if (lastPlayerTarget instanceof NPC && ((NPC) lastPlayerTarget).isDead())
		{
			lastPlayerTarget = null;
		}

		int tick = client.getTickCount();
		expected.removeIf(hit -> hit.toTick < tick);
		unclaimed.removeIf(seen -> seen.tick < tick);
		seenProjectiles.removeIf(projectile -> projectile.getRemainingCycles() <= 0);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
				reset();
				break;
			case HOPPING:
			case LOADING:
				expected.clear();
				unclaimed.clear();
				lastPlayerTarget = null;
				seenProjectiles.clear();
				break;
			default:
				break;
		}
	}

	private void record(int damage, Actor target, int tick, @Nullable ThrallType type)
	{
		log.debug("Thrall hit {} on {} at tick {}", damage, target.getName(), tick);
		fights.onThrallHit(damage, tick, type);
	}

	private static final class ExpectedHit
	{
		final Actor target;
		@Nullable
		final ThrallType type;
		final int fromTick;
		final int toTick;

		ExpectedHit(Actor target, @Nullable ThrallType type, int fromTick, int toTick)
		{
			this.target = target;
			this.type = type;
			this.fromTick = fromTick;
			this.toTick = toTick;
		}
	}

	private static final class SeenHitsplat
	{
		final Actor target;
		final int amount;
		final int tick;

		SeenHitsplat(Actor target, int amount, int tick)
		{
			this.target = target;
			this.amount = amount;
			this.tick = tick;
		}
	}
}
