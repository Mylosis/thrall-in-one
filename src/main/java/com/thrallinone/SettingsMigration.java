package com.thrallinone;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;

/**
 * Moves settings saved by 1.2.1 and earlier onto the 1.3.0 settings page,
 * once per RuneLite profile, so nothing a player sees changes on update,
 * except for what 1.3.0 cuts: reminders now always stay quiet where thralls
 * can't be summoned, and Low-Casts Notification is gone (the No Thrall Runes
 * box replaces it). It reads the raw saved values: RuneLite saves every setting's
 * default when the plugin loads, so a new key always reads as "set", while a
 * key no setting declares any more is only there if an older build saved it.
 *
 * <ul>
 * <li>Reminders Enabled off: Recast Box Off, both warnings off, Notify Me unticked. The No Thrall Runes
 * box is new and has no switch but Warn Below, so it still shows.</li>
 * <li>Casts Left Enabled off: the sidebar's casts box folded, and Warn Below 0 (so no No Thrall Runes box).</li>
 * <li>Low-Casts Notification: cut, so its saved value is removed.</li>
 * <li>Color by Type Off or Others: Your Color copied into your Mage, Ranged and Melee.</li>
 * <li>Color by Type Off or Yours: Others' Color copied into others' Mage, Ranged and Melee.</li>
 * </ul>
 *
 * <p>Recast Guard's Enabled and Block Left-Click, and the sound's Enabled and
 * Custom File, never shipped; only test profiles hold them. They are moved
 * too, so a test profile doesn't wake up with a sound playing.
 *
 * <p>An earlier migration (dropped before 1.0.0) saved versions up to 6 on
 * test profiles under the same key, so this one is version 7. Never reuse a
 * key from {@link #OLD_KEYS}: profiles may still hold it.
 */
@Slf4j
final class SettingsMigration
{
	/** Saved once the migration has run, so it never runs twice on one profile. */
	static final String VERSION_KEY = "settingsVersion";
	static final int VERSION = 7;

	/** Stands for "untick this notification, keeping the cog's choices" in a plan. */
	static final Object UNTICK = new Object();

	/** Keys this migration reads and removes. */
	static final List<String> OLD_KEYS = Collections.unmodifiableList(Arrays.asList(
		"remindersEnabled", "castsEnabled", "quietWhereNoSummon",
		"colorByTypeMode", "ownColor", "othersColor", "lowCastsNotification",
		// Never released: test profiles only.
		"recastGuardEnabled", "recastGuardBlockLeftClick", "recastSoundEnabled", "recastSoundFile"));

	/** Kept keys the plan reads. */
	private static final List<String> READ_KEYS = Arrays.asList("recastSound", ThrallPanel.FOLDED_KEY);

	/** 1.2.1's defaults for the single outline colors, as RuneLite saves a color. */
	static final String OLD_OWN_COLOR = String.valueOf(new Color(255, 200, 0, 200).getRGB());
	static final String OLD_OTHERS_COLOR = String.valueOf(new Color(255, 255, 255, 90).getRGB());

	private static final List<String> OWN_TYPE_KEYS = Arrays.asList("magicColor", "rangedColor", "meleeColor");
	private static final List<String> OTHERS_TYPE_KEYS = Arrays.asList(
		"othersMagicColor", "othersRangedColor", "othersMeleeColor");
	/** The sidebar's casts box, as {@link ThrallPanel} saves it folded. */
	static final String CASTS_BOX = "casts";

	private SettingsMigration()
	{
	}

	/** Run the migration on the current RuneLite profile if it hasn't run there yet. */
	static void run(ConfigManager configManager, ThrallInOneConfig config)
	{
		String group = ThrallInOneConfig.GROUP;
		if (savedVersion(configManager.getConfiguration(group, VERSION_KEY)) >= VERSION)
		{
			return;
		}

		Map<String, String> saved = new HashMap<>();
		for (String key : OLD_KEYS)
		{
			putIfSaved(saved, key, configManager.getConfiguration(group, key));
		}
		for (String key : READ_KEYS)
		{
			putIfSaved(saved, key, configManager.getConfiguration(group, key));
		}

		Map<String, Object> changes = plan(saved);
		if (!changes.isEmpty())
		{
			log.debug("Moving saved settings {} to {}", saved, changes);
		}
		for (Map.Entry<String, Object> change : changes.entrySet())
		{
			String key = change.getKey();
			Object value = change.getValue();
			if (value == null)
			{
				configManager.unsetConfiguration(group, key);
			}
			else if (value == UNTICK)
			{
				// Notify Me is the only notification left.
				Notification notification = config.expiryNotification();
				if (notification.isEnabled())
				{
					configManager.setConfiguration(group, key, notification.withEnabled(false));
				}
			}
			else
			{
				configManager.setConfiguration(group, key, value);
			}
		}
		configManager.setConfiguration(group, VERSION_KEY, VERSION);
	}

	/** The version a profile last migrated to; 0 if it never did (or the value is unreadable). */
	static int savedVersion(@Nullable String value)
	{
		if (value == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static void putIfSaved(Map<String, String> saved, String key, @Nullable String value)
	{
		if (value != null)
		{
			saved.put(key, value);
		}
	}

	/**
	 * What to change, from the settings saved under the group (key to saved
	 * string; keys that aren't saved are absent). Each entry is a key and its
	 * new value, {@link #UNTICK} for a notification, or null to remove the key.
	 */
	static Map<String, Object> plan(Map<String, String> saved)
	{
		Map<String, Object> changes = new LinkedHashMap<>();

		if ("false".equals(saved.get("remindersEnabled")))
		{
			changes.put("recastBox", RecastBox.OFF);
			changes.put("summonCheck", false);
			changes.put("bookWarning", false);
			changes.put("expiryNotification", UNTICK);
		}

		if ("false".equals(saved.get("castsEnabled")))
		{
			Set<String> folded = new LinkedHashSet<>();
			String foldedNow = saved.get(ThrallPanel.FOLDED_KEY);
			if (foldedNow != null && !foldedNow.isEmpty())
			{
				folded.addAll(Arrays.asList(foldedNow.split(",")));
			}
			if (folded.add(CASTS_BOX))
			{
				changes.put(ThrallPanel.FOLDED_KEY, String.join(",", folded));
			}
			// Warn Below at 0: no No Thrall Runes box for players who had casts left off.
			changes.put("castsWarnBelow", 0);
		}

		// Color by Type's values were OFF, YOURS (the default), OTHERS and BOTH.
		String byType = saved.get("colorByTypeMode");
		if ("OFF".equals(byType) || "OTHERS".equals(byType))
		{
			String own = saved.getOrDefault("ownColor", OLD_OWN_COLOR);
			OWN_TYPE_KEYS.forEach(key -> changes.put(key, own));
		}
		if ("OFF".equals(byType) || "YOURS".equals(byType))
		{
			String others = saved.getOrDefault("othersColor", OLD_OTHERS_COLOR);
			OTHERS_TYPE_KEYS.forEach(key -> changes.put(key, others));
		}

		// Test profiles: Enabled (off) and Block Left-Click (on) become one Guard dropdown.
		String guardEnabled = saved.get("recastGuardEnabled");
		if (guardEnabled != null)
		{
			RecastGuardMode mode = RecastGuardMode.OFF;
			if (Boolean.parseBoolean(guardEnabled))
			{
				mode = "false".equals(saved.get("recastGuardBlockLeftClick"))
					? RecastGuardMode.DIM : RecastGuardMode.DIM_AND_BLOCK;
			}
			changes.put("recastGuard", mode);
		}

		// Test profiles: a sound that wasn't on, or was Custom, becomes Off.
		String soundEnabled = saved.get("recastSoundEnabled");
		if (soundEnabled != null && !Boolean.parseBoolean(soundEnabled) || "CUSTOM".equals(saved.get("recastSound")))
		{
			changes.put("recastSound", ReminderSound.OFF);
		}

		for (String key : OLD_KEYS)
		{
			if (saved.containsKey(key))
			{
				changes.put(key, null);
			}
		}
		return changes;
	}
}
