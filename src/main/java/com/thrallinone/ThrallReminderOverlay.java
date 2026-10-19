package com.thrallinone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * "Recast Thrall" and the can't-summon warnings, above the chatbox. Each line
 * is its own filled box, like All-in-One Reminders' banner: the recast
 * box in the configured color, steady or pulsing, and the warnings in amber.
 * A preview of the recast box follows the Steady/Pulsing choice even while
 * the box is off.
 */
class ThrallReminderOverlay extends Overlay
{
	private static final String RECAST_TEXT = "Recast Thrall";
	private static final Color WARNING_FILL = new Color(168, 104, 18);

	private static final int PAD_X = 8;
	private static final int PAD_Y = 3;
	private static final int GAP = 3;
	/** One pulse every 1.4 s, dimming the fill to 72% at its lowest. */
	private static final long PULSE_MILLIS = 1400;
	private static final double PULSE_DEPTH = 0.28;

	private final ThrallReminders reminders;
	private final ThrallInOneConfig config;
	private final List<String> lines = new ArrayList<>(3);

	@Inject
	ThrallReminderOverlay(ThrallReminders reminders, ThrallInOneConfig config)
	{
		this.reminders = reminders;
		this.config = config;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean recast = reminders.showExpired();

		lines.clear();
		if (recast)
		{
			lines.add(RECAST_TEXT);
		}
		if (reminders.showWrongSpellbook())
		{
			lines.add("Not on the Arceuus Spellbook");
		}
		if (reminders.showMissingBook())
		{
			lines.add("No Book of the Dead");
		}
		if (lines.isEmpty())
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int width = 0;
		for (String line : lines)
		{
			width = Math.max(width, metrics.stringWidth(line));
		}
		width += 2 * PAD_X;
		int height = metrics.getHeight() + 2 * PAD_Y;

		Color recastFill = config.expiredBoxColor();
		boolean pulse = config.recastBox() == RecastBox.PULSING;
		int y = 0;
		for (int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i);
			boolean isRecast = recast && i == 0;
			Color fill = isRecast ? recastFill : WARNING_FILL;

			graphics.setColor(isRecast && pulse ? pulse(fill) : fill);
			graphics.fillRect(0, y, width, height);
			graphics.setColor(fill.darker());
			graphics.drawRect(0, y, width - 1, height - 1);

			int textX = (width - metrics.stringWidth(line)) / 2;
			int textY = y + PAD_Y + metrics.getAscent();
			graphics.setColor(Color.BLACK);
			graphics.drawString(line, textX + 1, textY + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(line, textX, textY);

			y += height + GAP;
		}
		return new Dimension(width, y - GAP);
	}

	private static Color pulse(Color color)
	{
		double phase = (System.currentTimeMillis() % PULSE_MILLIS) / (double) PULSE_MILLIS;
		double scale = 1 - PULSE_DEPTH * (1 - Math.cos(2 * Math.PI * phase)) / 2;
		return new Color(
			(int) (color.getRed() * scale),
			(int) (color.getGreen() * scale),
			(int) (color.getBlue() * scale),
			color.getAlpha());
	}
}
