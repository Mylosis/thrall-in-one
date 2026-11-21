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
 * Settings: one section per feature, only Reminders open. A setting that
 * belongs to the one above it is indented with four leading spaces in its
 * name, since RuneLite can't nest settings. RuneLite shows the tooltip as
 * "Name: description", so a child's description names what it belongs to.
 *
 * <p>Never rename a released key without a migration ({@link SettingsMigration}).
 */
@ConfigGroup(ThrallInOneConfig.GROUP)
public interface ThrallInOneConfig extends Config
{
	String GROUP = "thrallinone";

	@ConfigSection(
		name = "Reminders",
		description = "Tells you when to recast, and when you can't",
		position = 0
	)
	String remindersSection = "reminders";

	@ConfigSection(
		name = "Thralls",
		description = "How thralls look and sound, yours and everyone else's",
		position = 1,
		closedByDefault = true
	)
	String thrallsSection = "thralls";

	@ConfigSection(
		name = "Timer",
		description = "Seconds left, shown above your thrall",
		position = 2,
		closedByDefault = true
	)
	String timerSection = "timer";

	@ConfigSection(
		name = "Recast Guard",
		description = "Stops you recasting by accident while your thrall is out",
		position = 3,
		closedByDefault = true
	)
	String recastGuardSection = "recastGuard";

	@ConfigSection(
		name = "Casts Remaining",
		description = "How many more thralls your runes pay for",
		position = 4,
		closedByDefault = true
	)
	String castsSection = "casts";

	@ConfigSection(
		name = "General",
		description = "What counts as a fight, and the sidebar",
		position = 5,
		closedByDefault = true
	)
	String generalSection = "general";

	// Reminders: the recast rows, the two warnings, then Notify Me.

	@ConfigItem(
		keyName = "recastBox",
		name = "Recast Box",
		description = "A Recast Thrall box above the chat while you fight without a thrall",
		section = remindersSection,
		position = 0
	)
	default RecastBox recastBox()
	{
		return RecastBox.STEADY;
	}

	@ConfigItem(
		keyName = "expiredBoxColor",
		name = "    Color",
		description = "The box's fill color",
		section = remindersSection,
		position = 1
	)
	default Color expiredBoxColor()
	{
		return new Color(156, 43, 36);
	}

	@ConfigItem(
		keyName = "recastFlash",
		name = "Recast Flash",
		description = "Flash the screen for 2 seconds when it's time to recast",
		section = remindersSection,
		position = 2
	)
	default boolean recastFlash()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "recastFlashColor",
		name = "    Color",
		description = "The flash's color. More see-through is softer",
		section = remindersSection,
		position = 3
	)
	default Color recastFlashColor()
	{
		// RuneLite's own notification flash color.
		return new Color(255, 0, 0, 70);
	}

	@ConfigItem(
		keyName = "recastSound",
		name = "Recast Sound",
		description = "A sound just before your thrall runs out, or when it's time to recast. Picking one plays it",
		section = remindersSection,
		position = 4
	)
	default ReminderSound recastSound()
	{
		return ReminderSound.OFF;
	}

	@Range(min = 1, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "recastSoundVolume",
		name = "    Volume",
		description = "Plays at this volume even with game sounds off",
		section = remindersSection,
		position = 5
	)
	default int recastSoundVolume()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "summonCheck",
		name = "Spellbook Warning",
		description = "An amber box when you fight off the Arceuus spellbook, if you had a thrall out in the last 10 minutes",
		section = remindersSection,
		position = 6
	)
	default boolean summonCheck()
	{
		return false;
	}

	@ConfigItem(
		keyName = "bookWarning",
		name = "Book of the Dead Warning",
		description = "An amber box when you fight without the Book of the Dead, if you had a thrall out in the last 10 minutes",
		section = remindersSection,
		position = 7
	)
	default boolean bookWarning()
	{
		return true;
	}

	// Last in the section, though it fires with the recast rows only. Label renamed from Expiry
	// Notification; the key stays.
	@ConfigItem(
		keyName = "expiryNotification",
		name = "Notify Me",
		description = "A RuneLite notification for when you're tabbed out. The cog sets how, and can send it while you play",
		section = remindersSection,
		position = 8
	)
	default Notification expiryNotification()
	{
		return Notification.OFF;
	}

	// Thralls

	@ConfigItem(
		keyName = "ownShow",
		name = "Your Thrall",
		description = "How your thrall is drawn. The colors below are its outline by type; set all three alike for one color",
		section = thrallsSection,
		position = 0
	)
	default OwnThrallShow ownShow()
	{
		return OwnThrallShow.OUTLINE_ONLY;
	}

	@Alpha
	@ConfigItem(
		keyName = "magicColor",
		name = "    Mage",
		description = "Outline color for your ghostly thrall, or its imp or Deadman form",
		section = thrallsSection,
		position = 1
	)
	default Color magicColor()
	{
		return new Color(80, 160, 255, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "rangedColor",
		name = "    Ranged",
		description = "Outline color for your skeletal thrall, or its imp or Deadman form",
		section = thrallsSection,
		position = 2
	)
	default Color rangedColor()
	{
		return new Color(90, 220, 90, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "meleeColor",
		name = "    Melee",
		description = "Outline color for your zombified thrall, or its imp or Deadman form",
		section = thrallsSection,
		position = 3
	)
	default Color meleeColor()
	{
		return new Color(230, 70, 70, 220);
	}

	@ConfigItem(
		keyName = "othersShow",
		name = "Other Thralls",
		description = "How other players' thralls are drawn. Hidden and Outline Only hide their attacks too",
		section = thrallsSection,
		position = 4
	)
	default OthersThrallShow othersShow()
	{
		return OthersThrallShow.HIDDEN;
	}

	// Others' colors: the same hues as yours, fainter.

	@Alpha
	@ConfigItem(
		keyName = "othersMagicColor",
		name = "    Mage",
		description = "Outline color for other players' ghostly thralls",
		section = thrallsSection,
		position = 5
	)
	default Color othersMagicColor()
	{
		return new Color(80, 160, 255, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "othersRangedColor",
		name = "    Ranged",
		description = "Outline color for other players' skeletal thralls",
		section = thrallsSection,
		position = 6
	)
	default Color othersRangedColor()
	{
		return new Color(90, 220, 90, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "othersMeleeColor",
		name = "    Melee",
		description = "Outline color for other players' zombified thralls",
		section = thrallsSection,
		position = 7
	)
	default Color othersMeleeColor()
	{
		return new Color(230, 70, 70, 90);
	}

	@Range(min = 1, max = 50)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline Width",
		description = "How thick every thrall's outline is",
		section = thrallsSection,
		position = 8
	)
	default int outlineWidth()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "muteThralls",
		name = "Mute Attacks",
		description = "Silence thralls' attacks: other players', or everyone's",
		section = thrallsSection,
		position = 9
	)
	default MuteThralls muteThralls()
	{
		return MuteThralls.OFF;
	}

	@ConfigItem(
		keyName = "muteSummonSounds",
		name = "Mute Summon Sounds",
		description = "Silence every thrall rising and returning to the grave",
		section = thrallsSection,
		position = 10
	)
	default boolean muteSummonSounds()
	{
		return false;
	}

	// Timer

	@ConfigItem(
		keyName = "timerEnabled",
		name = "Show Timer",
		description = "Show the seconds left above your thrall",
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
		name = "    Text Size",
		description = "16 matches the game's text. Multiples of 16 stay sharpest",
		section = timerSection,
		position = 1
	)
	default int timerFontSize()
	{
		return ThrallTimerOverlay.BASE_FONT_SIZE;
	}

	@ConfigItem(
		keyName = "timerColor",
		name = "    Color",
		description = "The timer's normal color",
		section = timerSection,
		position = 2
	)
	default Color timerColor()
	{
		return Color.WHITE;
	}

	@Range(max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "timerWarnSeconds",
		name = "    Warn At",
		description = "Switch to the warning color with this many seconds left",
		section = timerSection,
		position = 3
	)
	default int timerWarnSeconds()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "timerWarnColor",
		name = "    Warning Color",
		description = "The timer's color from Warn At onwards",
		section = timerSection,
		position = 4
	)
	default Color timerWarnColor()
	{
		// The orange the game uses for interface highlights.
		return new Color(255, 152, 31);
	}

	// Recast Guard: off by default, so nobody's clicks change without them choosing it.

	@ConfigItem(
		keyName = "recastGuard",
		name = "Guard",
		description = "Dim the Resurrect spells while your thrall is out, or dim them and ignore left-clicks."
			+ " Right-click Cast always works",
		section = recastGuardSection,
		position = 0
	)
	default RecastGuardMode recastGuard()
	{
		return RecastGuardMode.OFF;
	}

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "recastGuardDim",
		name = "    Dim Amount",
		description = "How faded the Resurrect spells look. 0 leaves them as they are",
		section = recastGuardSection,
		position = 1
	)
	default int recastGuardDim()
	{
		return 60;
	}

	@Range(max = 30)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "recastGuardWindow",
		name = "    Recast Window",
		description = "Lets you recast this long before your thrall runs out. 0 guards until it's gone",
		section = recastGuardSection,
		position = 2
	)
	default int recastGuardWindow()
	{
		return 2;
	}

	// Casts Remaining

	// The casts warning (the in-game box and the sidebar's) has no setting of its own; 0 here is its
	// off switch. Notify When Low (key lowCastsNotification) was cut. Labels renamed from Casts Left
	// and Low Below; the keys stay.
	@Range(max = 100)
	@Units(" casts")
	@ConfigItem(
		keyName = "castsWarnBelow",
		name = "Warn Below",
		description = "Warns in game and in the sidebar when you have fewer casts than this,"
			+ " on the Arceuus spellbook with the Book of the Dead. 0 turns it off",
		section = castsSection,
		position = 0
	)
	default int castsWarnBelow()
	{
		return 10;
	}

	// General

	@Range(min = 5, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "combatTimeoutSeconds",
		name = "Fight Ends After",
		description = "A fight ends this long after the last hit, yours or theirs. Reminders and stats both use it",
		section = generalSection,
		position = 0
	)
	default int combatTimeoutSeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "sidebarPanel",
		name = "Sidebar Button",
		description = "A sidebar button for your thrall stats and casts remaining",
		section = generalSection,
		position = 1
	)
	default boolean sidebarPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showUpdateNotes",
		name = "Show Update Notes",
		description = "After an update, a chat line and a What's New box in the sidebar",
		section = generalSection,
		position = 2
	)
	default boolean showUpdateNotes()
	{
		return true;
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
