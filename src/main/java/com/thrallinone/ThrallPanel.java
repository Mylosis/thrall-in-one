package com.thrallinone;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
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
import java.awt.geom.Path2D;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
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
 * The sidebar panel: stats to look at and compare. From the top: the
 * "What's New" box after an update (until closed, {@link UpdateNoticeBox}), lifetime
 * tiles (summoned, prayer drained, gp spent), then boxes for this session,
 * lifetime, Mage / Ranged / Melee, raids and casts left, and a records
 * reset. Each box folds away from its title, and stays folded after a
 * restart. In RuneLite's developer mode, buttons at the very bottom preview
 * each reminder and the newest update notes. Updates arrive from {@link ThrallFights} and
 * {@link ThrallCasts} as copies, on the Swing thread; button actions go back
 * on the client thread.
 */
class ThrallPanel extends PluginPanel
{
	/** Box titles: an Arceuus purple, so the panel has its own identity. */
	static final Color ACCENT = new Color(0xB4, 0x8C, 0xFF);
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
	static final Font TITLE_FONT = FontManager.getRunescapeBoldFont().deriveFont(18f);
	/** The ⓘ in a box title, in the title purple; lighter while the mouse is over it. */
	private static final ImageIcon INFO_ICON = infoIcon(ACCENT);
	private static final ImageIcon INFO_ICON_HOVER = infoIcon(new Color(0xCF, 0xB4, 0xFF));
	/** Low on runes: a triangle beside the casts count, and a dot on the sidebar button. */
	private static final ImageIcon WARN_ICON = warnIcon();
	private static final BufferedImage NAV_ICON_LOW = withLowDot(ThrallInOnePlugin.NAV_ICON);
	/** Hidden setting: the ids of the folded boxes, comma separated. */
	static final String FOLDED_KEY = "panelFolded";

	private final ClientThread clientThread;
	private final ThrallFights fights;
	private final ThrallCasts casts;
	private final ThrallReminders reminders;
	private final ConfigManager configManager;
	private final UpdateNotifier updateNotifier;
	private final Set<String> folded = new LinkedHashSet<>();
	/** The "What's New" box at the very top, while there are notes to show. */
	@Nullable
	private UpdateNoticeBox noticeBox;

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
		ConfigManager configManager, ItemManager itemManager, SkillIconManager skillIcons, UpdateNotifier updateNotifier,
		@Named("developerMode") boolean developerMode)
	{
		this.clientThread = clientThread;
		this.fights = fights;
		this.casts = casts;
		this.reminders = reminders;
		this.configManager = configManager;
		this.updateNotifier = updateNotifier;
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
		castsBox = new FoldingBox("casts", "Casts Remaining");
		castsBox.setInfo("<html>Counts the runes you carry: inventory, rune pouch,<br>"
			+ "and any staff or tome you wear. Not your bank.</html>");
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
			FoldingBox previewBox = new FoldingBox("preview", "Preview");
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
		showCasts(-1, ThrallTier.GREATER, false, RuneWarning.NONE, true);
		showRecords(new ThrallRecords());
		updateNotes();
	}

	@Override
	public void onActivate()
	{
		updateNotes();
		refresh();
	}

	/** Show the update notes the player hasn't closed, or remove the box. Swing thread. */
	void updateNotes()
	{
		if (noticeBox != null)
		{
			remove(noticeBox);
			noticeBox = null;
		}
		List<UpdateNotice.Notice> notes = updateNotifier.pending();
		if (!notes.isEmpty())
		{
			noticeBox = new UpdateNoticeBox(notes, () ->
			{
				updateNotifier.close();
				updateNotes();
			});
			add(noticeBox, 0);
		}
		revalidate();
		repaint();
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

		// Full width under the reminders: it isn't one of them.
		JButton notes = new JButton("Update Notes");
		notes.setFocusPainted(false);
		notes.setToolTipText("Show the newest update notes at the top of this panel, and their chat line if you're"
			+ " logged in (unless Show Update Notes is off)");
		notes.addActionListener(e -> clientThread.invoke(() ->
		{
			updateNotifier.showLatestForTesting();
			SwingUtilities.invokeLater(this::updateNotes);
		}));
		JPanel content = new JPanel(new BorderLayout(0, 4));
		content.setOpaque(false);
		content.add(buttons, BorderLayout.CENTER);
		content.add(notes, BorderLayout.SOUTH);
		return content;
	}

	/**
	 * Casts left for the tier we'd cast next; -1 before it's counted. Low
	 * turns the count orange. The casts warning ({@link RuneWarning}, the
	 * same one the game screen shows) adds its words on the count's line and
	 * the dot on the sidebar button. Swing thread.
	 */
	void showCasts(int left, ThrallTier tier, boolean low, RuneWarning warning, boolean canSummon)
	{
		showLowDot(warning != RuneWarning.NONE);
		JPanel content = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		content.setOpaque(false);
		Tile tile = new Tile(ColorScheme.DARK_GRAY_COLOR, false);
		tile.setWarning(warning == RuneWarning.NONE ? null : warning == RuneWarning.OUT ? "Out of Runes" : "Low on Runes");
		// Casts left shows from login, before any fight.
		if (left < 0)
		{
			tile.set("-", "Shown Once You Log In", Color.WHITE, null);
		}
		else
		{
			tile.set(StatsFormat.number(left), tier.getDisplayName() + " Thralls", low ? LOW_COLOR : Color.WHITE,
				tier.getDisplayName() + " thralls you can cast with what you carry");
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
				+ " prayer points spent on Resurrect casts");
		long gp = records.getGpSpent();
		gpTile.set(StatsFormat.shortNumber(gp), "GP Spent", gp >= 10_000_000 ? GP_GREEN : gp >= 100_000 ? Color.WHITE : GP_YELLOW,
			StatsFormat.number(gp) + " gp of runes, priced on the day of each cast");

		lifetimeBox.setContent(totalsTiles(lifetime, StatsFormat.longDuration(lifetime.getTicks()), "Fight Time",
			"Time in counted fights, thrall out or not"));

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
			Color.WHITE, "Your thrall hit " + StatsFormat.number(totals.getThrallDamage()) + ". You hit "
				+ StatsFormat.number(totals.getOwnDamage())));
		tiles.add(new Tile(tileColor, false).set(StatsFormat.shortNumber(totals.getFights()), "Fights", Color.WHITE,
			"Fights with a thrall out, or one in the 10 minutes before"));
		tiles.add(new Tile(tileColor, false).set(StatsFormat.percent(totals.share()), "Share", Color.WHITE,
			"Your thrall's part of your combined damage"));
		tiles.add(new Tile(tileColor, false).set(StatsFormat.percent(totals.uptime()), "Uptime", Color.WHITE,
			"How much of your fight time your thrall was out"));
		double expected = totals.expectedDps();
		tiles.add(new Tile(tileColor, false).set(StatsFormat.dps(totals.measuredDps()),
			expected > 0 ? "DPS / " + StatsFormat.dps(expected) : "DPS", Color.WHITE,
			"Measured / the wiki's figure for its tier"));
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
				+ StatsFormat.plural(savedSummons, "summon", "summons") + " will be gone for good.",
			"Really Reset Thrall Records?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, second, second[1]);
		if (answer == 0)
		{
			clientThread.invoke(fights::resetRecords);
		}
	}

	/**
	 * Fold the boxes as the current RuneLite profile saved them: after a
	 * profile switch, so the old profile's folds aren't shown or saved over
	 * the new one's. Swing thread.
	 */
	void reloadFolded()
	{
		folded.clear();
		readFolded();
		for (FoldingBox box : new FoldingBox[]{sessionBox, lifetimeBox, typeBox, raidsBox, castsBox})
		{
			box.updateFold();
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

	/** A 16 px warning triangle in the low color. */
	private static ImageIcon warnIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		Path2D triangle = new Path2D.Double();
		triangle.moveTo(8, 1.5);
		triangle.lineTo(15, 14.5);
		triangle.lineTo(1, 14.5);
		triangle.closePath();
		g.setColor(LOW_COLOR);
		g.fill(triangle);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(7, 6, 2, 5);
		g.fillRect(7, 12, 2, 2);
		g.dispose();
		return new ImageIcon(image);
	}

	/** The sidebar button's icon with an orange dot in its top right corner. */
	private static BufferedImage withLowDot(BufferedImage icon)
	{
		BufferedImage image = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.drawImage(icon, 0, 0, null);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int size = 7;
		int x = image.getWidth() - size;
		// A dark ring keeps the dot apart from the icon under it.
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fill(new Ellipse2D.Double(x - 1, -1, size + 2, size + 2));
		g.setColor(LOW_COLOR);
		g.fill(new Ellipse2D.Double(x, 0, size, size));
		g.dispose();
		return image;
	}

	/**
	 * An orange dot on our sidebar button while casts are low, so it shows
	 * with the panel closed. RuneLite has no call to change a button's icon,
	 * and removing and re-adding the button can close our open panel, or open
	 * the sidebar on the panel used before ours (ClientUI goes back through
	 * its tab history when the selected tab is removed). So the icon is set
	 * on the sidebar's own tab, whose component is our wrapped panel. If the
	 * sidebar is off, or ever stops being a tabbed pane, nothing happens.
	 * Swing thread.
	 */
	private void showLowDot(boolean low)
	{
		JPanel tab = getWrappedPanel();
		Container sidebar = tab.getParent();
		if (!(sidebar instanceof JTabbedPane))
		{
			return;
		}
		JTabbedPane tabs = (JTabbedPane) sidebar;
		int index = tabs.indexOfComponent(tab);
		if (index < 0)
		{
			return;
		}
		BufferedImage image = low ? NAV_ICON_LOW : ThrallInOnePlugin.NAV_ICON;
		// RuneLite sizes sidebar icons itself; match whatever it drew.
		Icon shown = tabs.getIconAt(index);
		if (shown != null && (shown.getIconWidth() != image.getWidth() || shown.getIconHeight() != image.getHeight()))
		{
			image = ImageUtil.resizeImage(image, shown.getIconWidth(), shown.getIconHeight());
		}
		tabs.setIconAt(index, new ImageIcon(image));
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
		/** A warning on the value's line, at the tile's right edge ("⚠ Low on Runes"); left-aligned tiles only. */
		private final JLabel mark = new JLabel(WARN_ICON);
		private final JLabel label = new JLabel();
		private final Font valueFont;

		Tile(Color background, boolean centered)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(background);
			setBorder(centered ? new EmptyBorder(8, 3, 7, 3) : new EmptyBorder(7, 7, 6, 4));
			float x = centered ? CENTER_ALIGNMENT : LEFT_ALIGNMENT;
			valueFont = centered ? TRIO_VALUE_FONT : TILE_VALUE_FONT;
			value.setFont(valueFont);
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
				add(value);
			}
			else
			{
				// The value in a row of its own, so the warning can sit beside it.
				JPanel valueRow = new JPanel();
				valueRow.setLayout(new BoxLayout(valueRow, BoxLayout.X_AXIS));
				valueRow.setOpaque(false);
				valueRow.setAlignmentX(x);
				mark.setFont(FontManager.getRunescapeFont());
				mark.setForeground(LOW_COLOR);
				mark.setBorder(new EmptyBorder(0, 6, 0, 3));
				mark.setVisible(false);
				valueRow.add(value);
				// The warning sits at the tile's right edge, on the value's line.
				valueRow.add(Box.createHorizontalGlue());
				valueRow.add(mark);
				add(valueRow);
			}
			add(label);
		}

		/** Show the warning triangle and these words after the value, or hide them (null). */
		void setWarning(@Nullable String text)
		{
			mark.setText(text);
			mark.setVisible(text != null);
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

		/**
		 * A top tile's value drops to the other tiles' size when it doesn't fit:
		 * a third of the panel is about 61 px with the scrollbar showing, and
		 * "387.9K" in the big font is 69 px, which Swing cut to "387....".
		 */
		@Override
		public void doLayout()
		{
			Insets insets = getInsets();
			int room = getWidth() - insets.left - insets.right;
			if (room > 0 && valueFont != TILE_VALUE_FONT)
			{
				boolean fits = getFontMetrics(valueFont).stringWidth(value.getText()) <= room;
				Font font = fits ? valueFont : TILE_VALUE_FONT;
				if (value.getFont() != font)
				{
					value.setFont(font);
				}
			}
			super.doLayout();
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
