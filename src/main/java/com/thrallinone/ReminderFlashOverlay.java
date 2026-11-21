package com.thrallinone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The Reminders section's Flash: the whole game view tinted on and off for
 * 2 s when the expiry notification fires (and the setting is on). The
 * plugin's own flash, not RuneLite's notification, so no tray or sound comes
 * with it and it shows while the game is focused.
 */
class ReminderFlashOverlay extends Overlay
{
	private final Client client;
	private final ThrallReminders reminders;

	@Inject
	ReminderFlashOverlay(Client client, ThrallReminders reminders)
	{
		this.client = client;
		this.reminders = reminders;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Color color = reminders.flashColor();
		if (color == null)
		{
			return null;
		}
		graphics.setColor(color);
		graphics.fillRect(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		return null;
	}
}
