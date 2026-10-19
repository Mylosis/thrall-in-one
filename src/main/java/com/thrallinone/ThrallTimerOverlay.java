package com.thrallinone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Time left, drawn above our thrall in RuneScape Bold with a black outline. */
class ThrallTimerOverlay extends Overlay
{
	private final ThrallTracker tracker;
	private final ThrallTimer timer;
	private final ThrallInOneConfig config;

	@Inject
	ThrallTimerOverlay(ThrallTracker tracker, ThrallTimer timer, ThrallInOneConfig config)
	{
		this.tracker = tracker;
		this.timer = timer;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		NPC thrall = tracker.getOwned();
		int remaining = timer.getRemainingTicks();
		if (thrall == null || remaining < 0)
		{
			return null;
		}

		String text = ThrallTimer.format(remaining);
		// Set before placing the text, which centers it with the current font.
		graphics.setFont(FontManager.getRunescapeBoldFont());
		Point location = thrall.getCanvasTextLocation(graphics, text, thrall.getLogicalHeight() + 40);
		if (location != null)
		{
			drawOutlined(graphics, text, location.getX(), location.getY(), timerColor(config, remaining));
		}
		return null;
	}

	/** Text with a 1 px black outline all round, so it reads on any ground. */
	private static void drawOutlined(Graphics2D graphics, String text, int x, int y, Color color)
	{
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y);
		graphics.drawString(text, x - 1, y);
		graphics.drawString(text, x, y + 1);
		graphics.drawString(text, x, y - 1);
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	private static Color timerColor(ThrallInOneConfig config, int remainingTicks)
	{
		// Compared in shown seconds, so every "5s" is the same color.
		return ThrallTimer.seconds(remainingTicks) <= config.timerWarnSeconds() ? config.timerWarnColor() : config.timerColor();
	}
}
