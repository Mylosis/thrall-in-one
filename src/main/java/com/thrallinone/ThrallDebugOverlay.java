package com.thrallinone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Labels each thrall with the tracker's verdict so ownership detection can be
 * checked in a crowd. Off by default.
 */
class ThrallDebugOverlay extends Overlay
{
	private static final Color MINE = new Color(0, 255, 255);
	private static final Color OTHER = new Color(200, 200, 200);

	private final Client client;
	private final ThrallTracker tracker;
	private final ThrallInOneConfig config;

	@Inject
	ThrallDebugOverlay(Client client, ThrallTracker tracker, ThrallInOneConfig config)
	{
		this.client = client;
		this.tracker = tracker;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.debugLabels())
		{
			return null;
		}

		Player me = client.getLocalPlayer();
		for (NPC npc : tracker.getThralls())
		{
			boolean mine = tracker.isOwned(npc);
			String label = (mine ? "MINE: " + tracker.getClaimReason() : "other")
				+ " | " + describe(npc.getInteracting(), me);
			// Above the timer text, which sits at the usual overhead height.
			Point location = npc.getCanvasTextLocation(graphics, label, npc.getLogicalHeight() + 80);
			if (location != null)
			{
				OverlayUtil.renderTextLocation(graphics, location, label, mine ? MINE : OTHER);
			}
		}

		if (me != null)
		{
			String state = !tracker.hasActiveThrall() ? "no thrall out"
				: tracker.getOwned() == null ? "thrall out, not found" : "thrall found";
			OverlayUtil.renderActorOverlay(graphics, me, "Thrall In One: " + state, OTHER);
		}
		return null;
	}

	private static String describe(Actor target, Player me)
	{
		if (target == null)
		{
			return "idle";
		}
		if (target == me)
		{
			return "-> you";
		}
		return (target instanceof Player ? "-> player " : "-> ") + target.getName();
	}
}
