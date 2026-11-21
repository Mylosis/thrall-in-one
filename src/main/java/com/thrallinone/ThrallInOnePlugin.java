package com.thrallinone;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Thrall In One",
	description = "Outline your thrall, hide everyone else's, and get told when to recast",
	tags = {"tio", "thrall", "arceuus", "necromancy", "resurrect", "outline", "highlight", "hide"}
)
public class ThrallInOnePlugin extends Plugin implements RenderCallback
{
	@Inject
	private ThrallInOneConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ThrallTracker tracker;

	@Inject
	private ThrallOutlineOverlay outlineOverlay;

	// Ownership labels exist only in developer mode (./gradlew run), toggled with ::thralllabels.
	@Inject
	private ThrallDebugOverlay debugOverlay;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Inject
	private ThrallTimer timer;

	@Inject
	private ThrallTimerOverlay timerOverlay;

	@Inject
	private ThrallDamage damage;

	@Inject
	private ThrallReminders reminders;

	@Inject
	private ThrallReminderOverlay reminderOverlay;

	@Inject
	private ReminderFlashOverlay flashOverlay;

	@Inject
	private NoSummonTracker noSummon;

	@Inject
	private TfaHelper tfaHelper;

	@Inject
	private RecastGuard recastGuard;

	@Inject
	private ThrallCasts casts;

	@Inject
	private ThrallProjectiles projectiles;

	@Inject
	private ThrallSounds sounds;

	// Fight stats and the sidebar panel
	@Inject
	private ThrallFights fights;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Provider<ThrallPanel> panelProvider;

	@Inject
	private UpdateNotifier updateNotifier;

	/** The sidebar button: its own 16 x 16 drawing of icon.png, filled to the edges so it reads at that size. */
	static final BufferedImage NAV_ICON = ImageUtil.loadImageResource(ThrallInOnePlugin.class, "sidebar_icon.png");

	private ThrallPanel panel;
	private NavigationButton navButton;

	// Read on every rendered entity (client thread), so cached instead of going
	// through the config proxy; written on the Swing thread when settings change.
	private volatile boolean hideOthers;
	private volatile boolean hideOwn;
	/** Registered as a render callback ({@link #setRenderHook}). */
	private boolean renderHooked;
	/** Between start-up and shut-down, so a late settings change can't hook rendering back in. */
	private volatile boolean running;

	@Override
	protected void startUp()
	{
		// First, so everything below (and the sidebar panel's folded boxes) reads the moved settings.
		SettingsMigration.run(configManager, config);
		cacheConfig();
		eventBus.register(tracker);
		eventBus.register(timer);
		eventBus.register(damage);
		eventBus.register(reminders);
		eventBus.register(noSummon);
		eventBus.register(tfaHelper);
		eventBus.register(recastGuard);
		eventBus.register(casts);
		eventBus.register(projectiles);
		sounds.reset();
		eventBus.register(sounds);
		clientThread.invoke(() ->
		{
			tracker.start();
			timer.reset();
			damage.reset();
			reminders.reset();
			noSummon.reset();
			tfaHelper.reset();
			casts.reset();
			projectiles.reset();
		});
		running = true;
		setRenderHook(hideOwn || hideOthers);
		overlayManager.add(outlineOverlay);
		overlayManager.add(timerOverlay);
		overlayManager.add(reminderOverlay);
		overlayManager.add(flashOverlay);
		if (developerMode)
		{
			overlayManager.add(debugOverlay);
		}
		// Before the panel exists, so it opens with the right notes.
		updateNotifier.start();
		eventBus.register(updateNotifier);
		startFightStats();
	}

	private void startFightStats()
	{
		eventBus.register(fights);
		clientThread.invoke(fights::reset);
		ThrallPanel started = panelProvider.get();
		panel = started;
		SwingUtilities.invokeLater(() ->
		{
			// Skip if the plugin was stopped (or restarted) before this ran.
			if (panel == started)
			{
				updateSidebar();
			}
		});
	}

	private void stopFightStats()
	{
		clientThread.invoke(fights::stop);
		eventBus.unregister(fights);
		setPanel(null);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	/** Add or remove the sidebar button to match the setting. Swing thread. */
	private void updateSidebar()
	{
		boolean show = panel != null && config.sidebarPanel();
		if (show && navButton == null)
		{
			navButton = NavigationButton.builder()
				.tooltip("Thrall In One")
				.icon(NAV_ICON)
				.priority(7)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navButton);
			setPanel(panel);
			panel.refresh();
		}
		else if (!show && navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			setPanel(null);
		}
	}

	/** Point everything that feeds the sidebar panel at it, or at nothing (null). */
	private void setPanel(@Nullable ThrallPanel target)
	{
		fights.setPanel(target);
		casts.setPanel(target);
	}

	@Override
	protected void shutDown()
	{
		stopFightStats();
		overlayManager.remove(outlineOverlay);
		overlayManager.remove(timerOverlay);
		overlayManager.remove(reminderOverlay);
		overlayManager.remove(flashOverlay);
		overlayManager.remove(debugOverlay);
		running = false;
		setRenderHook(false);
		eventBus.unregister(tracker);
		eventBus.unregister(timer);
		eventBus.unregister(damage);
		eventBus.unregister(reminders);
		eventBus.unregister(noSummon);
		eventBus.unregister(tfaHelper);
		eventBus.unregister(recastGuard);
		eventBus.unregister(casts);
		eventBus.unregister(projectiles);
		eventBus.unregister(sounds);
		eventBus.unregister(updateNotifier);
		clientThread.invoke(() ->
		{
			tracker.stop();
			projectiles.reset();
			recastGuard.reset();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ThrallInOneConfig.GROUP.equals(event.getGroup()))
		{
			cacheConfig();
			updateRenderHook();
			if ("sidebarPanel".equals(event.getKey()))
			{
				SwingUtilities.invokeLater(this::updateSidebar);
			}
			else if ("showUpdateNotes".equals(event.getKey()))
			{
				SwingUtilities.invokeLater(this::updateNotes);
			}
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (developerMode && "thralllabels".equalsIgnoreCase(event.getCommand()))
		{
			configManager.setConfiguration(ThrallInOneConfig.GROUP, "debugLabels", !config.debugLabels());
		}
		// Shows the newest update notes again, as the panel's Update Notes button does.
		else if (developerMode && "thrallnotes".equalsIgnoreCase(event.getCommand()))
		{
			updateNotifier.showLatestForTesting();
			SwingUtilities.invokeLater(this::updateNotes);
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		SettingsMigration.run(configManager, config);
		cacheConfig();
		updateRenderHook();
		updateNotifier.start();
		SwingUtilities.invokeLater(() ->
		{
			// After the migration, which may have folded the casts box on this profile.
			if (panel != null)
			{
				panel.reloadFolded();
			}
			updateNotes();
		});
	}

	/** Swing thread. */
	private void updateNotes()
	{
		if (panel != null)
		{
			panel.updateNotes();
		}
	}

	private void cacheConfig()
	{
		hideOthers = !config.othersShow().isModel();
		hideOwn = !config.ownShow().isModel();
	}

	/**
	 * After a settings change: hook into rendering, or out of it, to match.
	 * Done on the client thread, which also draws, so it never lands
	 * mid-frame, and it reads the settings as they are by then.
	 */
	private void updateRenderHook()
	{
		clientThread.invoke(() ->
		{
			if (running)
			{
				setRenderHook(hideOwn || hideOthers);
			}
		});
	}

	/**
	 * Hook into rendering only while a thrall can be hidden: with both shown
	 * as models, {@link #addEntity} would keep everything, so it isn't asked.
	 */
	private synchronized void setRenderHook(boolean on)
	{
		if (on != renderHooked)
		{
			renderHooked = on;
			if (on)
			{
				renderCallbackManager.register(this);
			}
			else
			{
				renderCallbackManager.unregister(this);
			}
		}
	}

	@Override
	public boolean addEntity(Renderable renderable, boolean ui)
	{
		if (renderable instanceof NPC)
		{
			NPC npc = (NPC) renderable;
			if (!ThrallType.isThrall(npc.getId()))
			{
				return true;
			}
			return tracker.isOwned(npc) ? !hideOwn : !hideOthers;
		}

		// Other players' thralls' projectiles go with their models.
		if (renderable instanceof Projectile && hideOthers)
		{
			return !projectiles.isOthers((Projectile) renderable);
		}

		return true;
	}

	@Provides
	ThrallInOneConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ThrallInOneConfig.class);
	}
}
