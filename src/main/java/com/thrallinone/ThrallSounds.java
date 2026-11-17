package com.thrallinone;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;

/**
 * Silences thralls' sounds. The game sends every sound without its source
 * actor, so thrall sounds are known by id (names from the wiki's List of
 * sound IDs; there are no gameval constants for sounds). Attacks are area
 * sounds played on the attacking thrall's tile, which tells ours from other
 * players'. The Resurrect spells' sounds are plain sounds played to the caster
 * alone. The Deadman thralls (Zuk, Kalphite Princess and Wisp) have no sounds
 * of their own in the game's data, so they are covered by the standard ones.
 */
@Singleton
public class ThrallSounds
{
	/**
	 * Attack sounds that other things share, so muted only on a thrall's tile:
	 * ghostly "waterstrike_cast_and_fire", skeletal "longbow", zombified "zombie_attack".
	 * The imps play them too.
	 */
	static final Set<Integer> SHARED_ATTACK_SOUNDS = ImmutableSet.of(211, 2700, 918);
	/**
	 * The Leagues imp thralls' attack sounds ("l6_imp_magic_attack_*", "l6_imp_melee_attack_*",
	 * "l6_imp_ranged_attack_*"), used by nothing else. The magic projectile's sounds may play
	 * along its flight rather than on the imp.
	 */
	static final Set<Integer> IMP_ATTACK_SOUNDS = ImmutableSet.<Integer>builder()
		.addAll(range(11375, 11382))
		.add(11398, 11399)
		.build();
	/**
	 * Raising and losing a thrall: the Resurrect spells' "cast_and_fire" sounds (lesser, superior and
	 * greater ghost, skeleton and zombie), "resurrect_end" (expiry, or a recast over it), and the
	 * Leagues imps' spawn, transition and despawn sounds.
	 */
	static final Set<Integer> SUMMON_SOUNDS = ImmutableSet.<Integer>builder()
		.add(5061, 5028, 5063)
		.add(5025, 5039, 5048)
		.add(5040, 5057, 5058)
		.add(5054)
		.addAll(range(11370, 11374))
		.addAll(range(11383, 11397))
		.build();
	/** A sound this many tiles from a thrall is that thrall's. */
	private static final int SOUND_TILES = 1;
	/** An imp's projectile sound can play this far from the imp that fired it. */
	private static final int PROJECTILE_TILES = 10;

	private static final Predicate<NPC> ANY_THRALL = npc -> true;
	private static final Predicate<NPC> IMP = npc -> ThrallNpcs.isImp(npc.getId());

	private final ThrallInOneConfig config;
	private final ThrallTracker tracker;

	// Read for every sound the game plays, so cached instead of going through
	// the config proxy; written when settings change.
	private volatile MuteThralls muteAttacks = MuteThralls.OFF;
	private volatile boolean muteSummons;

	@Inject
	ThrallSounds(ThrallInOneConfig config, ThrallTracker tracker)
	{
		this.config = config;
		this.tracker = tracker;
	}

	private static Set<Integer> range(int first, int last)
	{
		return IntStream.rangeClosed(first, last).boxed().collect(Collectors.toSet());
	}

	/** Read the mute settings. */
	void reset()
	{
		muteAttacks = config.muteThralls();
		muteSummons = config.muteSummonSounds();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ThrallInOneConfig.GROUP.equals(event.getGroup()))
		{
			reset();
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		reset();
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		MuteThralls attacks = muteAttacks;
		if (attacks == MuteThralls.OFF && !muteSummons)
		{
			return;
		}

		int id = event.getSoundId();
		if (SUMMON_SOUNDS.contains(id))
		{
			if (muteSummons)
			{
				event.consume();
			}
			return;
		}
		if (attacks == MuteThralls.OFF)
		{
			return;
		}

		int x = event.getSceneX();
		int y = event.getSceneY();
		if (SHARED_ATTACK_SOUNDS.contains(id))
		{
			NPC thrall = thrallNear(x, y, SOUND_TILES, ANY_THRALL);
			if (thrall != null && attacks.mutes(tracker.isOwned(thrall)))
			{
				event.consume();
			}
		}
		else if (IMP_ATTACK_SOUNDS.contains(id))
		{
			// Only an imp makes these, so a ghost standing closer isn't the one that played it.
			NPC imp = thrallNear(x, y, PROJECTILE_TILES, IMP);
			if (imp != null ? attacks.mutes(tracker.isOwned(imp)) : attacks == MuteThralls.ALL)
			{
				event.consume();
			}
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		MuteThralls attacks = muteAttacks;
		if (attacks == MuteThralls.OFF && !muteSummons)
		{
			return;
		}

		int id = event.getSoundId();
		if (SUMMON_SOUNDS.contains(id))
		{
			if (muteSummons)
			{
				event.consume();
			}
		}
		// A plain sound is played to us alone, so the imp is ours.
		else if (IMP_ATTACK_SOUNDS.contains(id) && attacks.mutes(true))
		{
			event.consume();
		}
	}

	/** The thrall matching the filter nearest a sound's tile, within the given tiles. */
	@Nullable
	private NPC thrallNear(int sceneX, int sceneY, int tiles, Predicate<NPC> filter)
	{
		NPC nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (NPC thrall : tracker.getThralls())
		{
			LocalPoint location = thrall.getLocalLocation();
			if (location == null || !filter.test(thrall))
			{
				continue;
			}
			int distance = Math.max(Math.abs(location.getSceneX() - sceneX), Math.abs(location.getSceneY() - sceneY));
			if (distance <= tiles && distance < nearestDistance)
			{
				nearest = thrall;
				nearestDistance = distance;
			}
		}
		return nearest;
	}
}
