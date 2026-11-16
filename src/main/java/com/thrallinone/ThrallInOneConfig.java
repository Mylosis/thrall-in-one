package com.thrallinone;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Settings, in the order players change them most, with Title Case names.
 */
@ConfigGroup(ThrallInOneConfig.GROUP)
public interface ThrallInOneConfig extends Config
{
	String GROUP = "thrallinone";

	// Loose item first: RuneLite sorts sectionless items in among the sections by position.

	@ConfigItem(
		keyName = "sidebarPanel",
		name = "Sidebar Button",
		description = "Show Thrall In One's button in the sidebar: this session, casts left, lifetime stats,"
			+ " raids, and what your summons cost",
		position = 0
	)
	default boolean sidebarPanel()
	{
		return true;
	}

	@ConfigSection(
		name = "Thralls",
		description = "Which thralls are drawn, and their colors",
		position = 1
	)
	String thrallsSection = "thralls";

	@ConfigSection(
		name = "Outline",
		description = "Outline width, and which thralls are colored by type",
		position = 2
	)
	String outlineSection = "outline";

	@ConfigSection(
		// "»" reads as part of Outline above: RuneLite can't nest sections, and
		// RuneScape Bold has no "↳".
		name = "» Your Type Colors",
		description = "Outline colors for your thrall by type, used while Color by Type is Yours or Both",
		position = 3,
		closedByDefault = true
	)
	String yourTypeColorsSection = "yourTypeColors";

	@ConfigSection(
		name = "» Others' Type Colors",
		description = "Outline colors for other players' thralls by type, used while Color by Type is Others or Both",
		position = 4,
		closedByDefault = true
	)
	String othersTypeColorsSection = "othersTypeColors";

	@ConfigSection(
		name = "Timer",
		description = "Time left on your thrall, shown above it",
		position = 5
	)
	String timerSection = "timer";

	@ConfigSection(
		name = "Reminders",
		description = "Reminders when your thrall runs out or can't be cast",
		position = 6
	)
	String remindersSection = "reminders";

	@ConfigSection(
		name = "Casts Left",
		description = "Casts you have runes for, shown in the sidebar panel",
		position = 7
	)
	String castsSection = "casts";

	// Thralls

	@ConfigItem(
		keyName = "ownShow",
		name = "Your Thrall",
		description = "How your thrall is drawn: with an outline, outline only, as normal, or not at all",
		section = thrallsSection,
		position = 0
	)
	default OwnThrallShow ownShow()
	{
		return OwnThrallShow.OUTLINE_ONLY;
	}

	@Alpha
	@ConfigItem(
		keyName = "ownColor",
		name = "Your Color",
		description = "Outline color for your thrall. Unused while Color by Type (Outline section) is Yours or Both",
		section = thrallsSection,
		position = 1
	)
	default Color ownColor()
	{
		return new Color(255, 200, 0, 200);
	}

	@ConfigItem(
		keyName = "othersShow",
		name = "Other Thralls",
		description = "How other players' thralls are drawn. While their models are hidden (Hidden or Outline Only),"
			+ " the bolts and spells they fire are hidden too",
		section = thrallsSection,
		position = 2
	)
	default OthersThrallShow othersShow()
	{
		return OthersThrallShow.HIDDEN;
	}

	@Alpha
	@ConfigItem(
		keyName = "othersColor",
		name = "Others' Color",
		description = "Outline color for other players' thralls. Unused while Color by Type (Outline section) is Others or Both",
		section = thrallsSection,
		position = 3
	)
	default Color othersColor()
	{
		return new Color(255, 255, 255, 90);
	}

	@ConfigItem(
		keyName = "muteThralls",
		name = "Mute Attacks",
		description = "Silence thralls' attack sounds: other players' thralls only, or every thrall",
		section = thrallsSection,
		position = 4
	)
	default MuteThralls muteThralls()
	{
		return MuteThralls.OFF;
	}

	@ConfigItem(
		keyName = "muteSummonSounds",
		name = "Mute Summon Sounds",
		description = "Silence thralls being raised (the Resurrect spell's sound) and returning to the grave,"
			+ " including the Leagues imps' arrivals and departures",
		section = thrallsSection,
		position = 5
	)
	default boolean muteSummonSounds()
	{
		return false;
	}

	// Outline

	@Range(min = 1, max = 50)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "outlineWidth",
		name = "Width",
		description = "Outline width",
		section = outlineSection,
		position = 0
	)
	default int outlineWidth()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "colorByTypeMode",
		name = "Color by Type",
		description = "Color outlines by the thrall's type (Mage, Ranged, Melee) instead of Your Color or Others' Color:"
			+ " for your thrall, other players' thralls, or both. The colors are in the Type Colors sections below",
		section = outlineSection,
		position = 1
	)
	default ColorByType colorByType()
	{
		return ColorByType.YOURS;
	}

	// Your Type Colors

	@Alpha
	@ConfigItem(
		keyName = "magicColor",
		name = "Mage",
		description = "Outline color for your mage thrall (ghostly, or the magic imp and Deadman cosmetics)",
		section = yourTypeColorsSection,
		position = 0
	)
	default Color magicColor()
	{
		return new Color(80, 160, 255, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "rangedColor",
		name = "Ranged",
		description = "Outline color for your ranged thrall (skeletal, or the ranged imp and Deadman cosmetics)",
		section = yourTypeColorsSection,
		position = 1
	)
	default Color rangedColor()
	{
		return new Color(90, 220, 90, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "meleeColor",
		name = "Melee",
		description = "Outline color for your melee thrall (zombified, or the melee imp and Deadman cosmetics)",
		section = yourTypeColorsSection,
		position = 2
	)
	default Color meleeColor()
	{
		return new Color(230, 70, 70, 220);
	}

	// Others' Type Colors: the same hues as yours, as faint as Others' Color.

	@Alpha
	@ConfigItem(
		keyName = "othersMagicColor",
		name = "Mage",
		description = "Outline color for other players' mage thralls",
		section = othersTypeColorsSection,
		position = 0
	)
	default Color othersMagicColor()
	{
		return new Color(80, 160, 255, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "othersRangedColor",
		name = "Ranged",
		description = "Outline color for other players' ranged thralls",
		section = othersTypeColorsSection,
		position = 1
	)
	default Color othersRangedColor()
	{
		return new Color(90, 220, 90, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "othersMeleeColor",
		name = "Melee",
		description = "Outline color for other players' melee thralls",
		section = othersTypeColorsSection,
		position = 2
	)
	default Color othersMeleeColor()
	{
		return new Color(230, 70, 70, 90);
	}

	// Timer. Each feature section opens with its own Enabled tick, on by default.

	@ConfigItem(
		keyName = "timerEnabled",
		name = "Enabled",
		description = "Show the time left above your thrall",
		section = timerSection,
		position = 0
	)
	default boolean timerEnabled()
	{
		return true;
	}

	@Range(min = ThrallTimerOverlay.MIN_FONT_SIZE, max = ThrallTimerOverlay.MAX_FONT_SIZE)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "timerFontSize",
		name = "Font Size",
		description = "Size of the time left. 16 matches the game's own text; multiples of 16 stay the sharpest",
		section = timerSection,
		position = 1
	)
	default int timerFontSize()
	{
		return ThrallTimerOverlay.BASE_FONT_SIZE;
	}

	@Range(max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "timerWarnSeconds",
		name = "Warn At",
		description = "Turn the timer to the warning color with this many seconds left",
		section = timerSection,
		position = 2
	)
	default int timerWarnSeconds()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "timerColor",
		name = "Color",
		description = "Color of the time left",
		section = timerSection,
		position = 3
	)
	default Color timerColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "timerWarnColor",
		name = "Warning Color",
		description = "Color of the time left near the end",
		section = timerSection,
		position = 4
	)
	default Color timerWarnColor()
	{
		// The orange the game uses for interface highlights.
		return new Color(255, 152, 31);
	}

	// Reminders

	@ConfigItem(
		keyName = "remindersEnabled",
		name = "Enabled",
		description = "Turn every reminder below on or off at once. Fight Ends After still applies to fight stats",
		section = remindersSection,
		position = 0
	)
	default boolean remindersEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recastBox",
		name = "Recast Box",
		description = "A \"Recast Thrall\" box above the chatbox while you fight without a thrall",
		section = remindersSection,
		position = 1
	)
	default RecastBox recastBox()
	{
		return RecastBox.STEADY;
	}

	@ConfigItem(
		keyName = "expiredBoxColor",
		name = "Box Color",
		description = "Fill color of the Recast Thrall box (unused while Recast Box is Off)",
		section = remindersSection,
		position = 2
	)
	default Color expiredBoxColor()
	{
		return new Color(156, 43, 36);
	}

	@ConfigItem(
		keyName = "summonCheck",
		name = "Spellbook Warning",
		description = "An amber \"Not on the Arceuus Spellbook\" box when you're not on the Arceuus spellbook,"
			+ " while fighting within 10 minutes of having a thrall out",
		section = remindersSection,
		position = 3
	)
	default boolean summonCheck()
	{
		return false;
	}

	@ConfigItem(
		keyName = "bookWarning",
		name = "Book of the Dead Warning",
		description = "An amber \"No Book of the Dead\" box when you don't have the Book of the Dead with you,"
			+ " while fighting within 10 minutes of having a thrall out",
		section = remindersSection,
		position = 4
	)
	default boolean bookWarning()
	{
		return true;
	}

	@ConfigItem(
		keyName = "quietWhereNoSummon",
		name = "Quiet in Restricted Areas",
		description = "No reminders where thralls can't be summoned: Nightmare Zone, your house, Guardians of the Rift,"
			+ " on a boat, quest bosses that block thralls (Galvek, Vanstrom Klause and others), and anywhere"
			+ " the game has told you that you can't resurrect a thrall",
		section = remindersSection,
		position = 5
	)
	default boolean quietWhereNoSummon()
	{
		return true;
	}

	@Range(min = 5, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "combatTimeoutSeconds",
		name = "Fight Ends After",
		description = "You count as fighting while you attack an NPC, and until this long after your last hit"
			+ " or a hit on you. Reminders and fight stats both use it",
		section = remindersSection,
		position = 6
	)
	default int combatTimeoutSeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "expiryNotification",
		name = "Expiry Notification",
		description = "Notify when your thrall runs out in a fight, or when you start fighting without one."
			+ " RuneLite only sends it while the game isn't focused, unless you turn on Send When Focused."
			+ " The cog sets sound, tray and flash",
		section = remindersSection,
		position = 7
	)
	default Notification expiryNotification()
	{
		return Notification.OFF;
	}

	// Casts Left

	@ConfigItem(
		keyName = "castsEnabled",
		name = "Enabled",
		description = "Show casts left in the sidebar panel, and allow the Low-Casts Notification",
		section = castsSection,
		position = 0
	)
	default boolean castsEnabled()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@Units(" casts")
	@ConfigItem(
		keyName = "castsWarnBelow",
		name = "Low Below",
		description = "Casts left counts as low when fewer than this are left: orange in the sidebar panel,"
			+ " and the Low-Casts Notification",
		section = castsSection,
		position = 1
	)
	default int castsWarnBelow()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "lowCastsNotification",
		name = "Low-Casts Notification",
		description = "Notify once when casts left drops below Low Below, while your thrall is out or you're fighting."
			+ " It fires again only after you've restocked above the mark",
		section = castsSection,
		position = 2
	)
	default Notification lowCastsNotification()
	{
		return Notification.OFF;
	}

	// Developer mode only: never in the settings panel, toggled with ::thralllabels.

	@ConfigItem(
		keyName = "debugLabels",
		name = "Ownership Labels",
		description = "Label every thrall with whether the plugin thinks it is yours, why, and who it is interacting with",
		hidden = true
	)
	default boolean debugLabels()
	{
		return false;
	}
}
