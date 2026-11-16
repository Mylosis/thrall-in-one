package com.thrallinone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Thrall casts left from the runes in the inventory and rune pouch, with
 * worn staves and tomes supplying their runes. The tier counted is the one
 * we last cast (taken from our claimed thrall's NPC ID and saved to the
 * RuneLite profile), or greater before the first cast. Counted
 * on the game tick after the inventory, equipment, rune pouch or setting
 * changes, and sent to the sidebar panel. Below the "Low below" mark it can
 * notify once.
 */
@Slf4j
@Singleton
public class ThrallCasts
{
	private static final String LAST_TIER_KEY = "lastThrallTier";

	private final Client client;
	private final ThrallTracker tracker;
	private final ThrallReminders reminders;
	private final ThrallInOneConfig config;
	private final ConfigManager configManager;
	private final Notifier notifier;

	private int castsLeft = -1;
	/** The low-casts notification has fired for this drop below the low mark. */
	private boolean lowNotified;
	/** Where the count goes; null while the sidebar panel is off. */
	@Nullable
	private volatile ThrallPanel panel;
	/** We carry the Book of the Dead and are on the Arceuus spellbook: the count is worth showing. */
	private boolean canSummon;
	@Nullable
	private ThrallTier lastTier;
	private boolean lastTierLoaded;
	@Nullable
	private NPC seenOwned;
	private boolean dirty;
	/** What the panel was last sent, so a recount that changes nothing isn't sent again. */
	@Nullable
	private String pushed;

	@Inject
	ThrallCasts(Client client, ThrallTracker tracker, ThrallReminders reminders, ThrallInOneConfig config,
		ConfigManager configManager, Notifier notifier)
	{
		this.client = client;
		this.tracker = tracker;
		this.reminders = reminders;
		this.config = config;
		this.configManager = configManager;
		this.notifier = notifier;
	}

	void reset()
	{
		castsLeft = -1;
		canSummon = false;
		lastTier = null;
		lastTierLoaded = false;
		seenOwned = null;
		dirty = true;
		pushed = null;
		pushToPanel();
	}

	/** The tier counted: the last one we cast, or greater before our first cast. */
	public ThrallTier getNextTier()
	{
		return lastTier != null ? lastTier : ThrallTier.GREATER;
	}

	public boolean isLow()
	{
		return castsLeft >= 0 && castsLeft < config.castsWarnBelow();
	}

	void setPanel(@Nullable ThrallPanel panel)
	{
		this.panel = panel;
	}

	/** Push the count to a freshly shown panel. Client thread. */
	void refreshPanel()
	{
		pushed = null;
		pushToPanel();
	}

	/**
	 * Send the count to the panel if it changed since the last push. The
	 * inventory changes with nearly every attack or spell, and rebuilding the
	 * box each time would drop its tooltip mid-fight.
	 */
	private void pushToPanel()
	{
		ThrallPanel target = panel;
		if (target == null)
		{
			return;
		}
		boolean enabled = config.castsEnabled();
		int left = castsLeft;
		ThrallTier tier = getNextTier();
		boolean low = isLow();
		boolean summon = canSummon;
		String shown = enabled + "/" + left + "/" + tier + "/" + low + "/" + summon;
		if (shown.equals(pushed))
		{
			return;
		}
		pushed = shown;
		SwingUtilities.invokeLater(() -> target.showCasts(enabled, left, tier, low, summon));
	}

	/**
	 * The low-casts notification, once per drop below the low mark: while we
	 * could summon, and our thrall is out or we're fighting, so it doesn't
	 * fire at the bank. Counting again above the mark re-arms it.
	 */
	private void checkLow()
	{
		if (castsLeft >= 0 && !isLow())
		{
			lowNotified = false;
		}
		else if (!lowNotified && canSummon && isLow() && (tracker.hasActiveThrall() || reminders.isInCombat()))
		{
			lowNotified = true;
			// Counts as sent while off, so turning Casts Left back on doesn't fire for an old drop.
			if (!config.castsEnabled())
			{
				return;
			}
			notifier.notify(config.lowCastsNotification(), castsLeft == 0
				? "No thrall casts left"
				: "Low on thrall casts: " + castsLeft + " left");
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int id = event.getVarbitId();
		if (id == VarbitID.SPELLBOOK || RunePouch.isPouchVarbit(id))
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ThrallInOneConfig.GROUP.equals(event.getGroup())
			&& ("castsWarnBelow".equals(event.getKey()) || "castsEnabled".equals(event.getKey())))
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!lastTierLoaded)
		{
			// The RuneLite profile is the logged-in account's by the first tick.
			lastTierLoaded = true;
			lastTier = loadLastTier();
			dirty = true;
		}

		NPC owned = tracker.getOwned();
		if (owned != seenOwned)
		{
			seenOwned = owned;
			ThrallTier tier = owned == null ? null : ThrallTier.of(owned.getId());
			if (tier != null && tier != lastTier)
			{
				lastTier = tier;
				configManager.setRSProfileConfiguration(ThrallInOneConfig.GROUP, LAST_TIER_KEY, tier.name());
				log.debug("Last thrall tier now {}", tier);
				dirty = true;
			}
		}

		if (dirty)
		{
			dirty = false;
			count();
			pushToPanel();
		}
		checkLow();
	}

	private void count()
	{
		Map<Integer, Integer> carried = new HashMap<>();
		boolean pouch = false;
		boolean book = false;

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				int id = item.getId();
				if (id > 0)
				{
					carried.merge(id, item.getQuantity(), Integer::sum);
					pouch |= RunePouch.ITEMS.contains(id);
					book |= id == ItemID.BOOK_OF_THE_DEAD;
				}
			}
		}

		List<Integer> worn = new ArrayList<>();
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			for (Item item : equipment.getItems())
			{
				if (item.getId() > 0)
				{
					worn.add(item.getId());
					book |= item.getId() == ItemID.BOOK_OF_THE_DEAD;
				}
			}
		}

		// The pouch keeps its contents in varbits, which stay set while the
		// pouch is in the bank, so they only count with a pouch on us.
		if (pouch)
		{
			RunePouch.addContents(client, carried);
		}

		canSummon = book && client.getVarbitValue(VarbitID.SPELLBOOK) == ThrallReminders.ARCEUUS_SPELLBOOK;
		castsLeft = CastsCounter.castsLeft(getNextTier(), carried, worn);
	}

	@Nullable
	private ThrallTier loadLastTier()
	{
		String saved = configManager.getRSProfileConfiguration(ThrallInOneConfig.GROUP, LAST_TIER_KEY);
		if (saved == null)
		{
			return null;
		}
		try
		{
			return ThrallTier.valueOf(saved);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
