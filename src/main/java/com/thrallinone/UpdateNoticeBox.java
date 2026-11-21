package com.thrallinone;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The "What's New" box at the top of the sidebar panel, styled like the
 * panel's other boxes: each note as "Section > Setting: what it does", under
 * a heading per version when there are several, and a "Got It" button that
 * closes it for good.
 */
class UpdateNoticeBox extends JPanel
{
	/**
	 * Room for a note's text: the panel with its scrollbar, less the panel's
	 * and the box's sides and the bullet (5 px and a 6 px gap). Any wider and
	 * the text wraps to one line more than its height allows.
	 */
	static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 2 * 8 - 2 * 8 - 11;
	private static final ImageIcon BULLET = bullet(ThrallPanel.ACCENT);

	UpdateNoticeBox(List<UpdateNotice.Notice> notices, Runnable onClose)
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JLabel title = new JLabel("What's New");
		title.setFont(ThrallPanel.TITLE_FONT);
		title.setForeground(ThrallPanel.ACCENT);
		add(title);

		boolean several = notices.size() > 1;
		for (UpdateNotice.Notice notice : notices)
		{
			JLabel version = new JLabel(several ? notice.getVersion() : "Version " + notice.getVersion());
			version.setFont(several ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont());
			version.setForeground(several ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			add(version);
			for (UpdateNotice.Note note : notice.getNotes())
			{
				add(noteRow(note));
			}
		}

		JButton gotIt = new JButton("Got It");
		gotIt.setToolTipText("Close these notes for good");
		gotIt.setFocusPainted(false);
		gotIt.addActionListener(e -> onClose.run());
		JPanel button = new JPanel(new BorderLayout());
		button.setOpaque(false);
		button.setBorder(new EmptyBorder(3, 0, 0, 0));
		button.add(gotIt);
		add(button);
	}

	/** A bullet, then the setting in white and what it does in gray, wrapped to the box. */
	private static JPanel noteRow(UpdateNotice.Note note)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel bullet = new JLabel(BULLET);
		bullet.setVerticalAlignment(SwingConstants.TOP);
		bullet.setBorder(new EmptyBorder(6, 1, 0, 0));
		row.add(bullet, BorderLayout.WEST);

		String text = escape(note.getText());
		if (note.getSetting() != null)
		{
			text = "<font color='#ffffff'>" + escape(note.getSetting()) + ":</font> " + text;
		}
		JLabel label = new WrappedLabel("<html>" + text + "</html>");
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(label, BorderLayout.CENTER);
		return row;
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static ImageIcon bullet(Color color)
	{
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, 4, 4);
		g.dispose();
		return new ImageIcon(image);
	}

	/** An HTML label as tall as its text wrapped to {@link #TEXT_WIDTH}. */
	private static final class WrappedLabel extends JLabel
	{
		WrappedLabel(String html)
		{
			super(html);
			setVerticalAlignment(SwingConstants.TOP);
		}

		@Override
		public Dimension getPreferredSize()
		{
			View view = (View) getClientProperty(BasicHTML.propertyKey);
			if (view == null)
			{
				return super.getPreferredSize();
			}
			view.setSize(TEXT_WIDTH, 0);
			return new Dimension(TEXT_WIDTH, (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)));
		}
	}
}
