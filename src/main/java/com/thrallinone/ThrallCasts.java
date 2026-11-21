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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Thrall casts left from the runes in the inventory and rune pouch, with
 * worn staves and tomes supplying their runes. The tier counted is the one
 * we last cast (taken from our claimed thrall's NPC ID and saved to the
 * RuneLite profile), or greater before the first cast. Counted
 * on the game tick after the inventory, equipment, rune pouch or setting
 * changes, and sent to the sidebar panel. Below Warn Below, on the Arceuus
 * spellbook with the Book of the Dead, there is one warning
 * ({@link RuneWarning}): the sidebar shows it, and {@link ThrallReminders}
 * shows it in game. It comes on 3 seconds late and never while the bank is
 * open ({@link RuneWarningGate}), so gearing up doesn't set it off.
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

	private int castsLeft = -1;
	/** The warning as the runes stand at the last count. */
	private RuneWarning due = RuneWarning.NONE;
	/** The warning shown: {@link #due}, once the gate lets it through. */
	private RuneWarning warning = RuneWarning.NONE;
	private final RuneWarningGate gate = new RuneWarningGate();
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
	/** Worn item ids by slot at the last worn change, or null before one. */
	@Nullable
	private int[] wornSeen;
	/** What the panel was last sent, so a recount that changes nothing isn't sent again. */
	@Nullable
	private String pushed;

	@Inject
	ThrallCasts(Client client, ThrallTracker tracker, ThrallReminders reminders, ThrallInOneConfig config,
		ConfigManager configManager)
	{
		this.client = client;
		this.tracker = tracker;
		this.reminders = reminders;
		this.config = config;
		this.configManager = configManager;
	}

	void reset()
	{
		castsLeft = -1;
		due = RuneWarning.NONE;
		warning = RuneWarning.NONE;
		gate.reset();
		reminders.setRuneWarning(warning);
		canSummon = false;
		lastTier = null;
		lastTierLoaded = false;
		seenOwned = null;
		// Forget the worn ids too: the next worn change always recounts, whatever happened while we weren't listening.
		wornSeen = null;
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
		int left = castsLeft;
		ThrallTier tier = getNextTier();
		boolean low = isLow();
		RuneWarning warn = warning;
		boolean summon = canSummon;
		String shown = left + "/" + tier + "/" + low + "/" + warn + "/" + summon;
		if (shown.equals(pushed))
		{
			return;
		}
		pushed = shown;
		SwingUtilities.invokeLater(() -> target.showCasts(left, tier, low, warn, summon));
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			dirty = true;
		}
		// The count only reads which items are worn, not how many: ammo used by every ranged attack
		// changes the worn quantities alone, and needs no recount.
		else if (event.getContainerId() == InventoryID.WORN && wornIdsChanged(event.getItemContainer()))
		{
			dirty = true;
		}
	}

	/** Whether the worn item ids differ from the last worn change seen; remembers these. */
	private boolean wornIdsChanged(@Nullable ItemContainer equipment)
	{
		Item[] items = equipment != null ? equipment.getItems() : new Item[0];
		int[] seen = wornSeen;
		boolean changed = seen == null || seen.length != items.length;
		if (changed)
		{
			seen = new int[items.length];
			wornSeen = seen;
		}
		for (int i = 0; i < items.length; i++)
		{
			int id = items[i].getId();
			if (seen[i] != id)
			{
				seen[i] = id;
				changed = true;
			}
		}
		return changed;
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
			&& "castsWarnBelow".equals(event.getKey()))
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

		// The bank only matters while a warning is waiting to come on.
		boolean waiting = due != RuneWarning.NONE && warning == RuneWarning.NONE;
		RuneWarning shown = gate.tick(due, waiting && isBankOpen());
		if (shown != warning)
		{
			warning = shown;
			reminders.setRuneWarning(shown);
			pushToPanel();
		}
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
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
		due = RuneWarning.of(CastsCounter.castsWithCarried(getNextTier(), carried, worn),
			config.castsWarnBelow(), canSummon);
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
