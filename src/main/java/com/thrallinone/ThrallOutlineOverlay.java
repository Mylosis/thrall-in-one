package com.thrallinone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class ThrallOutlineOverlay extends Overlay
{
	/**
	 * How much of the outline's outer band fades (0 to 4).
	 */
	private static final int FEATHER = 3;

	private final ThrallTracker tracker;
	private final ThrallInOneConfig config;
	private final ThrallOutlineRenderer outlineRenderer;

	@Inject
	ThrallOutlineOverlay(ThrallTracker tracker, ThrallInOneConfig config, ThrallOutlineRenderer outlineRenderer)
	{
		this.tracker = tracker;
		this.config = config;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean outlineOwn = config.ownShow().isOutline();
		boolean outlineOthers = config.othersShow().isOutline();
		if (!outlineOwn && !outlineOthers)
		{
			return null;
		}

		int width = config.outlineWidth();
		int feather = FEATHER;
		if (!outlineOthers)
		{
			// Only ours to draw: no need to walk every thrall in the scene.
			NPC owned = tracker.getOwned();
			if (owned != null && tracker.isListed(owned))
			{
				outlineRenderer.drawOutline(owned, width, ownColor(owned), feather);
			}
			return null;
		}
		for (NPC npc : tracker.getThralls())
		{
			if (tracker.isOwned(npc))
			{
				if (outlineOwn)
				{
					outlineRenderer.drawOutline(npc, width, ownColor(npc), feather);
				}
			}
			else if (outlineOthers)
			{
				outlineRenderer.drawOutline(npc, width, othersColor(npc), feather);
			}
		}
		return null;
	}

	/** Your thrall's outline color for its type; Mage if its type isn't known. */
	private Color ownColor(NPC npc)
	{
		ThrallType type = ThrallType.of(npc.getId());
		if (type == ThrallType.RANGED)
		{
			return config.rangedColor();
		}
		return type == ThrallType.MELEE ? config.meleeColor() : config.magicColor();
	}

	/** Other players' outline color for the thrall's type; Mage if its type isn't known. */
	private Color othersColor(NPC npc)
	{
		ThrallType type = ThrallType.of(npc.getId());
		if (type == ThrallType.RANGED)
		{
			return config.othersRangedColor();
		}
		return type == ThrallType.MELEE ? config.othersMeleeColor() : config.othersMagicColor();
	}
}
