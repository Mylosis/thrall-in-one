package com.thrallinone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The "What's New" notes players see once after an update: a box at the top
 * of the sidebar panel, and one chat line at the first login after it. Only
 * releases worth announcing get a notice. To add one, put a new
 * {@link Notice} at the top of {@link #NOTICES} with the next id. A player
 * several notices behind sees every notice since the last one they closed.
 */
final class UpdateNotice
{
	/** Newest first; ids go up by one per notice. */
	static final List<Notice> NOTICES = Collections.unmodifiableList(Arrays.asList(
		new Notice(1, "1.3.0", "Smarter TFA reminders, Recast Guard, and a recast sound and flash",
			new Note("TFA Reminders", "Now reminds you when your thrall gets left behind, or when you enter"
				+ " a boss room without one."),
			new Note("Recast Guard > Guard", "Dims the Resurrect spells while your thrall is out, and can block"
				+ " left-clicks on them, so you don't recast by accident."),
			new Note("Reminders > Recast Sound", "Plays a sound just before your thrall runs out."),
			new Note("Reminders > Recast Flash", "Flashes the screen when it's time to recast."),
			new Note("Casts Remaining", "Now warns you when you're low on runes for thralls, or out of them."),
			new Note("Settings", "Reworked to feel more organized."),
			new Note("General > Show Update Notes", "Untick it to stop these notes."))
	));

	private UpdateNotice()
	{
	}

	/** The newest notice's id, or 0 with none. */
	static int latest(List<Notice> notices)
	{
		return notices.isEmpty() ? 0 : notices.get(0).getId();
	}

	/**
	 * The last notice this player has seen: what's saved, or on the first run
	 * with notices, 0 for an existing player (so they see the first notice)
	 * and the newest for a new install (so they see nothing).
	 */
	static int seenOrDefault(@Nullable Integer saved, boolean existingPlayer, List<Notice> notices)
	{
		if (saved != null)
		{
			return saved;
		}
		return existingPlayer ? 0 : latest(notices);
	}

	/** Notices newer than the last one seen, newest first; none while Show Update Notes is off. */
	static List<Notice> unseen(boolean enabled, int seen, List<Notice> notices)
	{
		List<Notice> unseen = new ArrayList<>();
		if (enabled)
		{
			for (Notice notice : notices)
			{
				if (notice.getId() > seen)
				{
					unseen.add(notice);
				}
			}
		}
		return unseen;
	}

	/** Whether to post the chat line at this login: there's a notice the player hasn't closed or been told about. */
	static boolean announce(boolean enabled, int seen, int announced, List<Notice> notices)
	{
		int latest = latest(notices);
		return enabled && latest > seen && latest > announced;
	}

	/** The chat line after the plugin's name, from the newest of the unseen notices. */
	static String chatText(List<Notice> unseen)
	{
		Notice newest = unseen.get(0);
		return "Updated to " + newest.getVersion() + ": " + newest.getSummary() + ". More in the sidebar.";
	}

	/** One release's notes. */
	static final class Notice
	{
		private final int id;
		private final String version;
		private final String summary;
		private final List<Note> notes;

		/**
		 * @param summary what the chat line says was added, with no full stop
		 *     ("Added TFA helpers")
		 */
		Notice(int id, String version, String summary, Note... notes)
		{
			this.id = id;
			this.version = version;
			this.summary = summary;
			this.notes = Collections.unmodifiableList(Arrays.asList(notes));
		}

		int getId()
		{
			return id;
		}

		String getVersion()
		{
			return version;
		}

		String getSummary()
		{
			return summary;
		}

		List<Note> getNotes()
		{
			return notes;
		}
	}

	/** One line of a notice: where the setting is, then what it does. */
	static final class Note
	{
		@Nullable
		private final String setting;
		private final String text;

		/**
		 * @param setting the setting's place as players see it ("Section > Setting"),
		 *     or null for a change with no setting
		 */
		Note(@Nullable String setting, String text)
		{
			this.setting = setting;
			this.text = text;
		}

		@Nullable
		String getSetting()
		{
			return setting;
		}

		String getText()
		{
			return text;
		}
	}
}
