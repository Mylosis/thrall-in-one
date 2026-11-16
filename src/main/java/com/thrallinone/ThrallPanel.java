package com.thrallinone;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * The sidebar panel: stats to look at and compare. From the top: lifetime
 * tiles (summoned, prayer drained, gp spent), then boxes for this session,
 * lifetime, Mage / Ranged / Melee, raids and casts left, and a records
 * reset. Each box folds away from its title, and stays folded after a
 * restart. In RuneLite's developer mode, buttons at the very bottom preview
 * each reminder. Updates arrive from {@link ThrallFights} and
 * {@link ThrallCasts} as copies, on the Swing thread; button actions go back
 * on the client thread.
 */
class ThrallPanel extends PluginPanel
{
	/** Box titles: an Arceuus purple, so the panel has its own identity. */
	private static final Color ACCENT = new Color(0xB4, 0x8C, 0xFF);
	/** Casts left below the low mark. */
	private static final Color LOW_COLOR = new Color(230, 150, 30);
	/** Column headings, quieter than labels. */
	private static final Color HEADING_COLOR = new Color(122, 122, 122);
	/** The game's coin stack colors: yellow below 100K, white below 10M, green from 10M. */
	private static final Color GP_YELLOW = new Color(255, 255, 0);
	private static final Color GP_GREEN = new Color(0, 255, 128);
	private static final int ICON_SIZE = 16;
	/** The top trio's icons and values, the biggest things in the panel. */
	private static final int TRIO_ICON_SIZE = 24;
	private static final BufferedImage SUMMONED_ICON = ImageUtil.loadImageResource(ThrallPanel.class, "icon.png");
	private static final Font TRIO_VALUE_FONT = FontManager.getRunescapeBoldFont().deriveFont(24f);
	/** Values in the other tiles (this session, lifetime, casts left). */
	private static final Font TILE_VALUE_FONT = FontManager.getRunescapeBoldFont().deriveFont(20f);
	private static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(18f);
	/** The ⓘ in a box title, in the title purple; lighter while the mouse is over it. */
	private static final ImageIcon INFO_ICON = infoIcon(ACCENT);
	private static final ImageIcon INFO_ICON_HOVER = infoIcon(new Color(0xCF, 0xB4, 0xFF));
	/** Hidden setting: the ids of the folded boxes, comma separated. */
	static final String FOLDED_KEY = "panelFolded";

	private final ClientThread clientThread;
	private final ThrallFights fights;
	private final ThrallCasts casts;
	private final ThrallReminders reminders;
	private final ConfigManager configManager;
	private final Set<String> folded = new LinkedHashSet<>();

	private final Tile summonedTile = new Tile(ColorScheme.DARKER_GRAY_COLOR, true);
	private final Tile prayerTile = new Tile(ColorScheme.DARKER_GRAY_COLOR, true);
	private final Tile gpTile = new Tile(ColorScheme.DARKER_GRAY_COLOR, true);
	private final FoldingBox sessionBox;
	private final FoldingBox lifetimeBox;
	private final FoldingBox typeBox;
	private final FoldingBox raidsBox;
	private final FoldingBox castsBox;
	private final JLabel[] typeIcons = new JLabel[ThrallType.values().length];

	// For the reset confirmation.
	private int savedFights;
	private int savedSummons;

	@Inject
	ThrallPanel(ClientThread clientThread, ThrallFights fights, ThrallCasts casts, ThrallReminders reminders,
		ConfigManager configManager, ItemManager itemManager, SkillIconManager skillIcons, @Named("developerMode") boolean developerMode)
	{
		this.clientThread = clientThread;
		this.fights = fights;
		this.casts = casts;
		this.reminders = reminders;
		this.configManager = configManager;
		readFolded();

		// Narrow sides, so the trio's labels fit at the regular font's size.
		setBorder(new EmptyBorder(10, 8, 10, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new DynamicGridLayout(0, 1, 0, 8));

		JPanel trio = new JPanel(new GridLayout(1, 3, 3, 0));
		trio.setOpaque(false);
		trio.add(summonedTile);
		trio.add(prayerTile);
		trio.add(gpTile);
		add(trio);

		sessionBox = new FoldingBox("session", "This Session");
		lifetimeBox = new FoldingBox("lifetime", "Lifetime");
		typeBox = new FoldingBox("byType", "By Type");
		raidsBox = new FoldingBox("raids", "Raids");
		castsBox = new FoldingBox("casts", "Casts Left");
		castsBox.setInfo("<html>Counted from what you carry right now: the runes in your inventory<br>"
			+ "and rune pouch, plus a staff or tome you're wearing. Your bank<br>"
			+ "isn't counted, so restocking brings the number back up.</html>");
		add(sessionBox);
		add(lifetimeBox);
		add(typeBox);
		add(raidsBox);
		add(castsBox);

		JButton reset = new JButton("Reset Records…");
		reset.setToolTipText("Clear this account's saved thrall records");
		reset.setFocusPainted(false);
		reset.addActionListener(e -> confirmReset());
		add(reset);

		// A testing aid, last so it reads as apart from the panel: shown only in
		// RuneLite's developer mode (./gradlew run), never to players.
		if (developerMode)
		{
			FoldingBox previewBox = new FoldingBox("preview", "Preview Reminder");
			previewBox.setContent(previewButtons());
			add(previewBox);
		}

		// The plugin's own icon (icon.png, as on the Plugin Hub), so the panel matches the sidebar button.
		summonedTile.setIcon(SUMMONED_ICON);
		prayerTile.setIcon(skillIcons.getSkillImage(Skill.PRAYER, false));
		AsyncBufferedImage coins = itemManager.getImage(ItemID.COINS, 10_000, false);
		coins.onLoaded(() -> SwingUtilities.invokeLater(() -> gpTile.setIcon(coins)));
		Skill[] typeSkills = {Skill.MAGIC, Skill.RANGED, Skill.ATTACK};
		for (ThrallType type : ThrallType.values())
		{
			typeIcons[type.ordinal()] = new JLabel(icon(skillIcons.getSkillImage(typeSkills[type.ordinal()], true)));
		}

		showSession(new FightTotals(), 0);
		showCasts(true, -1, ThrallTier.GREATER, false, true);
		showRecords(new ThrallRecords());
	}

	@Override
	public void onActivate()
	{
		refresh();
	}

	/** Ask everything that feeds the panel to send what it has now. Any thread. */
	void refresh()
	{
		clientThread.invoke(() ->
		{
			fights.refreshPanel();
			casts.refreshPanel();
		});
	}

	private JPanel previewButtons()
	{
		JPanel buttons = new JPanel(new GridLayout(0, 2, 4, 4));
		buttons.setOpaque(false);
		for (PreviewReminder reminder : PreviewReminder.values())
		{
			JButton button = new JButton(reminder.getName());
			button.setFocusPainted(false);
			button.setToolTipText("Show " + reminder.getShows() + " for "
				+ ThrallReminders.PREVIEW_MILLIS / 1000 + " seconds, whatever your reminder settings");
			button.addActionListener(e -> clientThread.invoke(() -> reminders.preview(reminder)));
			buttons.add(button);
		}
		return buttons;
	}

	/** Casts left for the tier we'd cast next; -1 before it's counted. Hidden while Casts Left is off. Swing thread. */
	void showCasts(boolean enabled, int left, ThrallTier tier, boolean low, boolean canSummon)
	{
		castsBox.setVisible(enabled);
		JPanel content = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		content.setOpaque(false);
		Tile tile = new Tile(ColorScheme.DARK_GRAY_COLOR, false);
		// Casts left shows from login, before any fight.
		if (left < 0)
		{
			tile.set("-", "Counted Once Logged In", Color.WHITE, null);
		}
		else
		{
			tile.set(StatsFormat.number(left), tier.getDisplayName() + " Thralls", low ? LOW_COLOR : Color.WHITE,
				tier.getDisplayName() + " thralls the runes you carry pay for, counting the rune pouch,"
					+ " staves and combination runes");
		}
		content.add(tile);
		if (left >= 0 && !canSummon)
		{
			content.add(rows(Arrays.asList(new Row("Can't Summon Now", "",
				"You need the Book of the Dead with you and the Arceuus spellbook", ColorScheme.LIGHT_GRAY_COLOR))));
		}
		castsBox.setContent(content);
	}

	/** This login session's finished fights and casts. Swing thread. */
	void showSession(FightTotals session, int sessionCasts)
	{
		sessionBox.setContent(totalsTiles(session, StatsFormat.shortNumber(sessionCasts), "Casts",
			"Thralls you've cast since you logged in"));
	}

	/** The saved records. Swing thread. */
	void showRecords(ThrallRecords records)
	{
		FightTotals lifetime = records.getLifetime();
		savedFights = lifetime.getFights();
		savedSummons = records.getSummons();

		summonedTile.set(StatsFormat.shortNumber(records.getSummons()), "Summoned", Color.WHITE,
			StatsFormat.number(records.getSummons()) + " thralls summoned on this account");
		prayerTile.set(StatsFormat.shortNumber(records.getPrayerSpent()), "Drained", Color.WHITE,
			StatsFormat.number(records.getPrayerSpent())
				+ " prayer points drained by your Resurrect casts: 2, 4 or 6 for lesser, superior or greater");
		long gp = records.getGpSpent();
		gpTile.set(StatsFormat.shortNumber(gp), "GP Spent", gp >= 10_000_000 ? GP_GREEN : gp >= 100_000 ? Color.WHITE : GP_YELLOW,
			StatsFormat.number(gp) + " gp spent on runes: what your casts' runes were worth on the day of each cast");

		lifetimeBox.setContent(totalsTiles(lifetime, StatsFormat.longDuration(lifetime.getTicks()), "Fight Time",
			"Time in fights that counted, with or without your thrall out"));

		typeBox.setContent(typeTable(records));

		List<Row> raidRows = new ArrayList<>();
		records.getBosses().entrySet().stream()
			.sorted(Comparator.comparingLong(
				(Map.Entry<String, ThrallRecords.BossRecord> raid) -> raid.getValue().getTotals().getThrallDamage()).reversed())
			.forEach(raid -> addRaid(raidRows, raid.getKey(), raid.getValue().getTotals()));
		// Raids only get a box once there's a raid fight to show.
		raidsBox.setVisible(!raidRows.isEmpty());
		raidsBox.setContent(rows(raidRows));
	}

	/** Six tiles: thrall damage, fights, share, uptime, DPS, and one more (casts or fight time). */
	private static JPanel totalsTiles(FightTotals totals, String lastValue, String lastLabel, String lastTooltip)
	{
		JPanel tiles = new JPanel(new GridLayout(0, 2, 4, 4));
		tiles.setOpaque(false);
		Color tileColor = ColorScheme.DARK_GRAY_COLOR;
		tiles.add(new Tile(tileColor, false).set(StatsFormat.shortNumber(totals.getThrallDamage()), "Thrall Damage",
			Color.WHITE, StatsFormat.number(totals.getThrallDamage()) + " damage from your thrall; yours was "
				+ StatsFormat.number(totals.getOwnDamage())));
		tiles.add(new Tile(tileColor, false).set(StatsFormat.shortNumber(totals.getFights()), "Fights", Color.WHITE,
			"Fights that counted: your thrall was out during the fight or in the 10 minutes before it"));
		tiles.add(new Tile(tileColor, false).set(StatsFormat.percent(totals.share()), "Share", Color.WHITE,
			"Your thrall's share of your damage (your hits plus its hits)"));
		tiles.add(new Tile(tileColor, false).set(StatsFormat.percent(totals.uptime()), "Uptime", Color.WHITE,
			"How much of your fights your thrall was out"));
		double expected = totals.expectedDps();
		tiles.add(new Tile(tileColor, false).set(StatsFormat.dps(totals.measuredDps()),
			expected > 0 ? "DPS / " + StatsFormat.dps(expected) : "DPS", Color.WHITE,
			"Measured while your thrall was out / the wiki's figure for its tier (lesser 0.208, superior 0.416, greater 0.625)"));
		tiles.add(new Tile(tileColor, false).set(lastValue, lastLabel, Color.WHITE, lastTooltip));
		return tiles;
	}

	/** Mage / Ranged / Melee: summoned and damage, with each style's skill icon. */
	private JPanel typeTable(ThrallRecords records)
	{
		JPanel table = new JPanel(new GridBagLayout());
		table.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(1, 0, 1, 0);
		c.gridy = 0;
		c.gridx = 2;
		table.add(label("Summoned", HEADING_COLOR, SwingConstants.RIGHT), c);
		c.gridx = 3;
		c.insets = new Insets(1, 8, 1, 0);
		table.add(label("Damage", HEADING_COLOR, SwingConstants.RIGHT), c);

		for (ThrallType type : ThrallType.values())
		{
			c.gridy++;
			c.gridx = 0;
			c.weightx = 0;
			c.insets = new Insets(1, 0, 1, 5);
			table.add(typeIcons[type.ordinal()], c);
			c.gridx = 1;
			c.weightx = 1;
			c.insets = new Insets(1, 0, 1, 0);
			table.add(label(type.getDisplayName(), ColorScheme.LIGHT_GRAY_COLOR, SwingConstants.LEFT), c);
			c.gridx = 2;
			c.weightx = 0;
			int summoned = records.getSummonsByType().getOrDefault(type, 0);
			JLabel summonedLabel = label(StatsFormat.shortNumber(summoned), Color.WHITE, SwingConstants.RIGHT);
			summonedLabel.setToolTipText(StatsFormat.number(summoned) + " " + type.getDisplayName().toLowerCase() + " thralls summoned");
			table.add(summonedLabel, c);
			c.gridx = 3;
			c.insets = new Insets(1, 8, 1, 0);
			long damage = records.getDamageByType().getOrDefault(type, 0L);
			JLabel damageLabel = label(StatsFormat.shortNumber(damage), Color.WHITE, SwingConstants.RIGHT);
			damageLabel.setToolTipText(StatsFormat.number(damage) + " damage from your " + type.getDisplayName().toLowerCase() + " thralls");
			table.add(damageLabel, c);
		}
		return table;
	}

	private static void addRaid(List<Row> rows, String name, FightTotals totals)
	{
		rows.add(Row.header(name, StatsFormat.plural(totals.getFights(), "fight", "fights")));
		rows.add(new Row("Thrall Damage", StatsFormat.number(totals.getThrallDamage()), null, Color.WHITE));
		rows.add(new Row("Share / Uptime", StatsFormat.percent(totals.share()) + " / " + StatsFormat.percent(totals.uptime()),
			null, Color.WHITE));
	}

	/** Reset goes through two prompts, the second defaulting to keeping the records. */
	private void confirmReset()
	{
		Object[] first = {"Reset…", "Cancel"};
		int answer = JOptionPane.showOptionDialog(this,
			"Reset thrall records? This clears every saved fight, summon and raid record for this account.",
			"Reset Thrall Records", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, first, first[1]);
		if (answer != 0)
		{
			return;
		}

		Object[] second = {"Reset Everything", "Keep My Records"};
		answer = JOptionPane.showOptionDialog(this,
			"Really reset? " + StatsFormat.plural(savedFights, "fight", "fights") + " and "
				+ StatsFormat.plural(savedSummons, "summon", "summons") + " will be gone for good. This can't be undone.",
			"Really Reset Thrall Records?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, second, second[1]);
		if (answer == 0)
		{
			clientThread.invoke(fights::resetRecords);
		}
	}

	private void readFolded()
	{
		String saved = configManager.getConfiguration(ThrallInOneConfig.GROUP, FOLDED_KEY);
		if (saved != null && !saved.isEmpty())
		{
			folded.addAll(Arrays.asList(saved.split(",")));
		}
	}

	private void saveFolded()
	{
		if (folded.isEmpty())
		{
			configManager.unsetConfiguration(ThrallInOneConfig.GROUP, FOLDED_KEY);
		}
		else
		{
			configManager.setConfiguration(ThrallInOneConfig.GROUP, FOLDED_KEY, String.join(",", folded));
		}
	}

	private static JLabel label(String text, Color color, int alignment)
	{
		JLabel label = new JLabel(text, alignment);
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(color);
		return label;
	}

	/**
	 * A circled "i", 16 px: a smooth 1.5 px ring,
	 * and the "i" on whole pixels so it stays crisp.
	 */
	private static ImageIcon infoIcon(Color color)
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(color);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(new Ellipse2D.Double(0.75, 0.75, 14.5, 14.5));
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.fillRect(7, 3, 2, 2);
		g.fillRect(7, 6, 2, 7);
		g.dispose();
		return new ImageIcon(image);
	}

	@Nullable
	private static ImageIcon icon(@Nullable BufferedImage image)
	{
		return icon(image, ICON_SIZE);
	}

	@Nullable
	private static ImageIcon icon(@Nullable BufferedImage image, int size)
	{
		return image == null ? null : new ImageIcon(ImageUtil.resizeImage(image, size, size, true));
	}

	/** Label / value rows, with bold headers between groups (raids). */
	private static JPanel rows(List<Row> rows)
	{
		JPanel body = new JPanel(new GridBagLayout());
		body.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		for (Row row : rows)
		{
			c.insets = new Insets(row.header && c.gridy > 0 ? 8 : 1, 0, 1, 0);
			JLabel label = label(row.label, row.header ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR, SwingConstants.LEFT);
			JLabel value = label(row.value, row.header ? ColorScheme.LIGHT_GRAY_COLOR : row.valueColor, SwingConstants.RIGHT);
			if (row.header)
			{
				label.setFont(FontManager.getRunescapeBoldFont());
			}
			label.setToolTipText(row.tooltip);
			value.setToolTipText(row.tooltip);
			c.gridx = 0;
			c.weightx = 1;
			body.add(label, c);
			c.gridx = 1;
			c.weightx = 0;
			body.add(value, c);
			c.gridy++;
		}
		return body;
	}

	/** One line of a box: a label on the left and its value on the right. */
	private static final class Row
	{
		final String label;
		final String value;
		@Nullable
		final String tooltip;
		final boolean header;
		final Color valueColor;

		Row(String label, String value, @Nullable String tooltip, Color valueColor)
		{
			this(label, value, tooltip, false, valueColor);
		}

		private Row(String label, String value, @Nullable String tooltip, boolean header, Color valueColor)
		{
			this.label = label;
			this.value = value;
			this.tooltip = tooltip;
			this.header = header;
			this.valueColor = valueColor;
		}

		static Row header(String label, String value)
		{
			return new Row(label, value, null, true, Color.WHITE);
		}
	}

	/**
	 * A big value over a gray label. Centered tiles are the lifetime trio:
	 * an icon on top and the biggest values in the panel.
	 */
	private static final class Tile extends JPanel
	{
		private final JLabel icon = new JLabel();
		private final JLabel value = new JLabel();
		private final JLabel label = new JLabel();

		Tile(Color background, boolean centered)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(background);
			setBorder(centered ? new EmptyBorder(8, 3, 7, 3) : new EmptyBorder(7, 7, 6, 4));
			float x = centered ? CENTER_ALIGNMENT : LEFT_ALIGNMENT;
			value.setFont(centered ? TRIO_VALUE_FONT : TILE_VALUE_FONT);
			value.setForeground(Color.WHITE);
			label.setFont(FontManager.getRunescapeFont());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			icon.setAlignmentX(x);
			value.setAlignmentX(x);
			label.setAlignmentX(x);
			if (centered)
			{
				icon.setBorder(new EmptyBorder(0, 0, 4, 0));
				add(icon);
			}
			add(value);
			add(label);
		}

		Tile set(String valueText, String labelText, Color valueColor, @Nullable String tooltip)
		{
			value.setText(valueText);
			value.setForeground(valueColor);
			label.setText(labelText);
			setToolTipText(tooltip);
			value.setToolTipText(tooltip);
			label.setToolTipText(tooltip);
			return this;
		}

		void setIcon(@Nullable BufferedImage image)
		{
			icon.setIcon(ThrallPanel.icon(image, TRIO_ICON_SIZE));
			revalidate();
			repaint();
		}
	}

	/** A titled box whose title folds it away; the folded state is saved. */
	private final class FoldingBox extends JPanel
	{
		private final String id;
		private final JLabel arrow = new JLabel();
		private final JLabel info = new JLabel(INFO_ICON);
		private final JPanel body = new JPanel(new BorderLayout());

		FoldingBox(String id, String titleText)
		{
			this.id = id;
			setLayout(new BorderLayout(0, 4));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(new EmptyBorder(8, 8, 8, 8));

			JPanel titleBar = new JPanel(new BorderLayout(5, 0));
			titleBar.setOpaque(false);
			titleBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			titleBar.setToolTipText("Click to fold or unfold");
			arrow.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			JLabel title = new JLabel(titleText);
			title.setFont(TITLE_FONT);
			title.setForeground(ACCENT);
			titleBar.add(arrow, BorderLayout.WEST);
			titleBar.add(title, BorderLayout.CENTER);
			info.setVisible(false);
			info.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					info.setIcon(INFO_ICON_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					info.setIcon(INFO_ICON);
				}
			});
			titleBar.add(info, BorderLayout.EAST);
			titleBar.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (!folded.remove(id))
					{
						folded.add(id);
					}
					saveFolded();
					updateFold();
				}
			});

			body.setOpaque(false);
			add(titleBar, BorderLayout.NORTH);
			add(body, BorderLayout.CENTER);
			updateFold();
		}

		/** An ⓘ at the title's right that explains the box on hover. */
		void setInfo(String tooltip)
		{
			info.setToolTipText(tooltip);
			info.setVisible(true);
		}

		void setContent(JPanel content)
		{
			body.removeAll();
			body.add(content, BorderLayout.CENTER);
			body.revalidate();
			body.repaint();
		}

		private void updateFold()
		{
			boolean open = !folded.contains(id);
			arrow.setText(open ? "▼" : "▶");
			body.setVisible(open);
			ThrallPanel.this.revalidate();
			ThrallPanel.this.repaint();
		}
	}
}
