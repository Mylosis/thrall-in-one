package com.thrallinone;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Recast Guard: while our thrall is out, dim the nine Resurrect spells (Dim
 * Only), or dim them and ignore plain left-clicks on them (Dim + Block), so a
 * misclick doesn't replace it (and spend the runes). Casting from the
 * right-click menu still works. Nothing is cast or added: a left-click is
 * only swallowed. Dim + Block with a Dim Amount of 0 blocks without dimming.
 *
 * <p>"Out" is the tracker's: our thrall from the spawn tick, then while the
 * varbit is on, but not once it's left behind in another instance, another
 * of Ocellus's quadrants or outside a TFA boss arena, where the varbit can
 * stay on and recasting is the point.
 *
 * <p>Recast Window (2 s by default): the guard lifts that long before the
 * thrall runs out, by the plugin's own count (the on-screen Timer can be
 * off), so an early recast to avoid a gap goes through and the spells
 * brighten as the cue. Recast Sound keeps its own fixed lead.
 */
@Slf4j
@Singleton
public class RecastGuard
{
	static final Set<Integer> SPELLS = ImmutableSet.of(
		InterfaceID.MagicSpellbook.RESURRECT_LESSER_GHOST,
		InterfaceID.MagicSpellbook.RESURRECT_LESSER_SKELETON,
		InterfaceID.MagicSpellbook.RESURRECT_LESSER_ZOMBIE,
		InterfaceID.MagicSpellbook.RESURRECT_SUPERIOR_GHOST,
		InterfaceID.MagicSpellbook.RESURRECT_SUPERIOR_SKELETON,
		InterfaceID.MagicSpellbook.RESURRECT_SUPERIOR_ZOMBIE,
		InterfaceID.MagicSpellbook.RESURRECT_GREATER_GHOST,
		InterfaceID.MagicSpellbook.RESURRECT_GREATER_SKELETON,
		InterfaceID.MagicSpellbook.RESURRECT_GREATER_ZOMBIE);
	/** {@link #SPELLS} as plain ints, for the per-frame dimming (no boxing). */
	private static final int[] SPELL_IDS = SPELLS.stream().mapToInt(Integer::intValue).toArray();

	private final Client client;
	private final ThrallInOneConfig config;
	private final ThrallTracker tracker;
	private final TfaHelper tfaHelper;
	private final ThrallTimer timer;

	/** The right-click menu was open, so the next click is a choice from it, not a left-click. */
	private boolean menuWasOpen;
	/** Opacity to give the spells this frame, or -1 to leave them alone; set each client tick. */
	private int dimOpacity = -1;
	/** Spell widgets we dimmed (bit i for {@link #SPELL_IDS}[i]), so only our own changes are undone. */
	private int dimmed;

	@Inject
	RecastGuard(Client client, ThrallInOneConfig config, ThrallTracker tracker, TfaHelper tfaHelper, ThrallTimer timer)
	{
		this.client = client;
		this.config = config;
		this.tracker = tracker;
		this.tfaHelper = tfaHelper;
		this.timer = timer;
	}

	/** Client thread. Undo any dimming, as on shut-down. */
	void reset()
	{
		menuWasOpen = false;
		dimOpacity = -1;
		undim();
	}

	/** Our thrall is out here, so a recast would only replace it. */
	private boolean guarding()
	{
		// From the spawn tick (claimed from our cast), before the varbit turns on. Checked first: it's
		// the cheapest, and false most of the time.
		if (!tracker.isVarbitOn() && tracker.getOwned() == null)
		{
			return false;
		}
		return config.recastGuard() != RecastGuardMode.OFF && !tracker.isLeftBehind() && !tfaHelper.isStranded()
			&& !inRecastWindow(timer.getRemainingTicks(), windowTicks(config.recastGuardWindow()));
	}

	/**
	 * Close enough to the end that a recast is a choice, not a misclick. An
	 * unknown time left (-1) or a window of 0 keeps the guard on.
	 */
	static boolean inRecastWindow(int remainingTicks, int windowTicks)
	{
		return windowTicks > 0 && remainingTicks >= 0 && remainingTicks <= windowTicks;
	}

	/**
	 * Whole ticks within the window, rounded down so it never opens earlier
	 * than set: 2 s is 3 ticks (1.8 s). Rounding up made it 4 (2.4 s), which
	 * felt a touch long in play.
	 */
	static int windowTicks(int seconds)
	{
		return Math.max(0, seconds) * 5 / 3;
	}

	/** A plain left-click on a Resurrect spell's Cast, which the guard swallows. */
	static boolean blocks(boolean guarding, boolean blockLeftClick, boolean fromMenu, int widgetId, MenuAction action)
	{
		return guarding && blockLeftClick && !fromMenu && SPELLS.contains(widgetId)
			&& (action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY);
	}

	/** Widget opacity (0 opaque, 255 clear) for a dim of 0 to 100 percent. */
	static int opacity(int percent)
	{
		int clamped = Math.max(0, Math.min(100, percent));
		// 254 at most: fully clear would look like the spell had gone.
		return clamped * 254 / 100;
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		menuWasOpen = true;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		boolean fromMenu = menuWasOpen;
		menuWasOpen = false;

		MenuEntry entry = event.getMenuEntry();
		if (blocks(guarding(), config.recastGuard().isBlocking(), fromMenu, entry.getParam1(), entry.getType()))
		{
			log.debug("Recast Guard: ignored a left-click on {} at tick {}", entry.getTarget(), client.getTickCount());
			event.consume();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!client.isMenuOpen())
		{
			menuWasOpen = false;
		}

		int dim = guarding() ? config.recastGuardDim() : 0;
		dimOpacity = dim > 0 ? opacity(dim) : -1;
	}

	/**
	 * Dim just before each frame is drawn. The spellbook's own script puts the
	 * spells back to full opacity as it redraws; dimming on the client tick
	 * left a frame of that showing every game tick, so the spells pulsed.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		int opacity = dimOpacity;
		if (opacity < 0)
		{
			undim();
			return;
		}

		for (int i = 0; i < SPELL_IDS.length; i++)
		{
			Widget widget = client.getWidget(SPELL_IDS[i]);
			if (widget != null)
			{
				widget.setOpacity(opacity);
				dimmed |= 1 << i;
			}
		}
	}

	private void undim()
	{
		if (dimmed == 0)
		{
			return;
		}
		for (int i = 0; i < SPELL_IDS.length; i++)
		{
			if ((dimmed & 1 << i) != 0)
			{
				Widget widget = client.getWidget(SPELL_IDS[i]);
				if (widget != null)
				{
					widget.setOpacity(0);
				}
			}
		}
		dimmed = 0;
	}
}
