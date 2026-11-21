package com.thrallinone;

import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.eventbus.Subscribe;

/**
 * Keeps track of which update notice this player has seen, and posts the
 * chat line at the first login after an update (see {@link UpdateNotice}).
 * Stored in the RuneLite profile, not per account, so each notice shows once.
 */
@Slf4j
@Singleton
class UpdateNotifier
{
	/** Hidden settings: the last notice closed, and the last one announced in chat. */
	static final String SEEN_KEY = "updateNoticeSeen";
	static final String ANNOUNCED_KEY = "updateNoticeAnnounced";
	private final Client client;
	private final ConfigManager configManager;
	private final ThrallInOneConfig config;
	private final PluginChat chat;
	private final List<UpdateNotice.Notice> notices = UpdateNotice.NOTICES;

	@Inject
	UpdateNotifier(Client client, ConfigManager configManager, ThrallInOneConfig config, PluginChat chat)
	{
		this.client = client;
		this.configManager = configManager;
		this.config = config;
		this.chat = chat;
	}

	/**
	 * On plugin start and profile switch. The first time, saves where this
	 * player starts from: a new install starts caught up, so it sees nothing.
	 * RuneLite saves every setting's default as soon as a plugin loads, so an
	 * existing player is told apart by the data this plugin saves as you play.
	 */
	void start()
	{
		if (savedInt(SEEN_KEY) != null)
		{
			return;
		}
		boolean existing = existingPlayer();
		int seen = UpdateNotice.seenOrDefault(null, existing, notices);
		configManager.setConfiguration(ThrallInOneConfig.GROUP, SEEN_KEY, seen);
		if (!existing)
		{
			configManager.setConfiguration(ThrallInOneConfig.GROUP, ANNOUNCED_KEY, seen);
		}
		log.debug("Update notices start at {} ({})", seen, existing ? "existing player" : "new install");
	}

	/** The notices to show in the sidebar, newest first. Any thread. */
	List<UpdateNotice.Notice> pending()
	{
		return UpdateNotice.unseen(config.showUpdateNotes(), seen(), notices);
	}

	/** The player closed the box: every notice so far is seen, and needs no chat line either. */
	void close()
	{
		int latest = UpdateNotice.latest(notices);
		configManager.setConfiguration(ThrallInOneConfig.GROUP, SEEN_KEY, latest);
		configManager.setConfiguration(ThrallInOneConfig.GROUP, ANNOUNCED_KEY, latest);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			announceIfDue();
		}
	}

	/**
	 * Developer mode only (the panel's Update Notes button, or ::thrallnotes):
	 * back to a player one notice behind, so the newest notice shows in the box
	 * and the chat line posts again. Client thread.
	 */
	void showLatestForTesting()
	{
		configManager.setConfiguration(ThrallInOneConfig.GROUP, SEEN_KEY, Math.max(0, UpdateNotice.latest(notices) - 1));
		configManager.unsetConfiguration(ThrallInOneConfig.GROUP, ANNOUNCED_KEY);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			announceIfDue();
		}
	}

	/** Post the chat line once per notice. Client thread. */
	private void announceIfDue()
	{
		int seen = seen();
		Integer announced = savedInt(ANNOUNCED_KEY);
		boolean enabled = config.showUpdateNotes();
		if (!UpdateNotice.announce(enabled, seen, announced != null ? announced : seen, notices))
		{
			return;
		}
		chat.post(UpdateNotice.chatText(UpdateNotice.unseen(enabled, seen, notices)));
		configManager.setConfiguration(ThrallInOneConfig.GROUP, ANNOUNCED_KEY, UpdateNotice.latest(notices));
	}

	private int seen()
	{
		Integer saved = savedInt(SEEN_KEY);
		// Saved by start(); if it's missing, show nothing rather than guess.
		return saved != null ? saved : UpdateNotice.latest(notices);
	}

	/** Any data this plugin saved for any account, or a folded panel box: someone who has used the plugin. */
	private boolean existingPlayer()
	{
		for (RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (!configManager.getRSProfileConfigurationKeys(ThrallInOneConfig.GROUP, profile.getKey(), "").isEmpty())
			{
				return true;
			}
		}
		return configManager.getConfiguration(ThrallInOneConfig.GROUP, ThrallPanel.FOLDED_KEY) != null;
	}

	@Nullable
	private Integer savedInt(String key)
	{
		String saved = configManager.getConfiguration(ThrallInOneConfig.GROUP, key);
		if (saved == null)
		{
			return null;
		}
		try
		{
			return Integer.parseInt(saved);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
