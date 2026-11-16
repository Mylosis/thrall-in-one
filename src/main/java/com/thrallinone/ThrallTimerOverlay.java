package com.thrallinone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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
	/** RuneScape Bold's own size: one font pixel per screen pixel. */
	static final int BASE_FONT_SIZE = 16;
	static final int MIN_FONT_SIZE = 8;
	static final int MAX_FONT_SIZE = 64;

	private final ThrallTracker tracker;
	private final ThrallTimer timer;
	private final ThrallInOneConfig config;

	// Derived once per size change, not every frame.
	private Font font;
	private int fontSize;

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
		int size = config.timerFontSize();
		// Set before placing the text, which centers it with the current font.
		graphics.setFont(font(size));
		Point location = thrall.getCanvasTextLocation(graphics, text, thrall.getLogicalHeight() + 40);
		if (location != null)
		{
			drawOutlined(graphics, text, location.getX(), location.getY(), outlineWidth(size), timerColor(config, remaining));
		}
		return null;
	}

	private Font font(int size)
	{
		if (font == null || fontSize != size)
		{
			Font base = FontManager.getRunescapeBoldFont();
			font = size == BASE_FONT_SIZE ? base : base.deriveFont((float) size);
			fontSize = size;
		}
		return font;
	}

	/** Black outline width in pixels: one font pixel, so it keeps up as the text grows. */
	static int outlineWidth(int fontSize)
	{
		return Math.max(1, Math.round(fontSize / (float) BASE_FONT_SIZE));
	}

	/**
	 * Text with a black outline all round, so it reads on any ground. A 1 px outline is the four
	 * straight neighbours; wider ones add the diagonals to fill the corners.
	 */
	private static void drawOutlined(Graphics2D graphics, String text, int x, int y, int width, Color color)
	{
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + width, y);
		graphics.drawString(text, x - width, y);
		graphics.drawString(text, x, y + width);
		graphics.drawString(text, x, y - width);
		if (width > 1)
		{
			graphics.drawString(text, x + width, y + width);
			graphics.drawString(text, x - width, y + width);
			graphics.drawString(text, x + width, y - width);
			graphics.drawString(text, x - width, y - width);
		}
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	private static Color timerColor(ThrallInOneConfig config, int remainingTicks)
	{
		// Compared in shown seconds, so every "5s" is the same color.
		return ThrallTimer.seconds(remainingTicks) <= config.timerWarnSeconds() ? config.timerWarnColor() : config.timerColor();
	}
}
