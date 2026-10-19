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
		ColorByType byType = config.colorByType();
		for (NPC npc : tracker.getThralls())
		{
			if (tracker.isOwned(npc))
			{
				if (outlineOwn)
				{
					outlineRenderer.drawOutline(npc, width, byType.isYours() ? ownTypeColor(npc) : config.ownColor(), feather);
				}
			}
			else if (outlineOthers)
			{
				outlineRenderer.drawOutline(npc, width, byType.isOthers() ? othersTypeColor(npc) : config.othersColor(), feather);
			}
		}
		return null;
	}

	/** Your Type Colors for this thrall's type, or Your Color if its type isn't known. */
	private Color ownTypeColor(NPC npc)
	{
		ThrallType type = ThrallType.of(npc.getId());
		if (type == null)
		{
			return config.ownColor();
		}
		switch (type)
		{
			case MAGIC:
				return config.magicColor();
			case RANGED:
				return config.rangedColor();
			default:
				return config.meleeColor();
		}
	}

	/** Others' Type Colors for this thrall's type, or Others' Color if its type isn't known. */
	private Color othersTypeColor(NPC npc)
	{
		ThrallType type = ThrallType.of(npc.getId());
		if (type == null)
		{
			return config.othersColor();
		}
		switch (type)
		{
			case MAGIC:
				return config.othersMagicColor();
			case RANGED:
				return config.othersRangedColor();
			default:
				return config.othersMeleeColor();
		}
	}
}
