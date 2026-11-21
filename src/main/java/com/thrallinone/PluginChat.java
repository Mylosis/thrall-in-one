package com.thrallinone;

import java.awt.Color;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/**
 * The plugin's own chat lines: "[Thrall In One]" in the plugin's purple,
 * picked for the chatbox as it is now, then the text in the player's own
 * game-message color.
 */
@Singleton
class PluginChat
{
	/** The panel's purple, darkened to read on the opaque (parchment) chatbox. */
	private static final Color NAME_ON_OPAQUE = new Color(0x5A, 0x1E, 0xA6);

	private final Client client;
	private final ChatMessageManager chatMessageManager;

	@Inject
	PluginChat(Client client, ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.chatMessageManager = chatMessageManager;
	}

	/** Post a line, as "[Thrall In One] text". Client thread. */
	void post(String text)
	{
		boolean transparent = client.isResized() && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) != 0;
		String message = new ChatMessageBuilder()
			.append(transparent ? ThrallPanel.ACCENT : NAME_ON_OPAQUE, "[Thrall In One]")
			.append(ChatColorType.NORMAL)
			.append(" " + text)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
