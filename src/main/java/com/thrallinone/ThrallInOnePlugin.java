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
	description = "Tells your own Arceuus thrall apart from everyone else's: outline yours, hide or tone down theirs",
	tags = {"thrall", "arceuus", "necromancy", "resurrect", "outline", "highlight", "hide"}
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
	private NoSummonTracker noSummon;

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

	/** The sidebar button: its own 16 x 16 drawing of icon.png, filled to the edges so it reads at that size. */
	private static final BufferedImage NAV_ICON = ImageUtil.loadImageResource(ThrallInOnePlugin.class, "sidebar_icon.png");

	private ThrallPanel panel;
	private NavigationButton navButton;

	// Read on every rendered entity (client thread), so cached instead of going
	// through the config proxy; written on the Swing thread when settings change.
	private volatile boolean hideOthers;
	private volatile boolean hideOwn;

	@Override
	protected void startUp()
	{
		cacheConfig();
		eventBus.register(tracker);
		eventBus.register(timer);
		eventBus.register(damage);
		eventBus.register(reminders);
		eventBus.register(noSummon);
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
			casts.reset();
			projectiles.reset();
		});
		renderCallbackManager.register(this);
		overlayManager.add(outlineOverlay);
		overlayManager.add(timerOverlay);
		overlayManager.add(reminderOverlay);
		if (developerMode)
		{
			overlayManager.add(debugOverlay);
		}
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
		overlayManager.remove(debugOverlay);
		renderCallbackManager.unregister(this);
		eventBus.unregister(tracker);
		eventBus.unregister(timer);
		eventBus.unregister(damage);
		eventBus.unregister(reminders);
		eventBus.unregister(noSummon);
		eventBus.unregister(casts);
		eventBus.unregister(projectiles);
		eventBus.unregister(sounds);
		clientThread.invoke(() ->
		{
			tracker.stop();
			projectiles.reset();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ThrallInOneConfig.GROUP.equals(event.getGroup()))
		{
			cacheConfig();
			if ("sidebarPanel".equals(event.getKey()))
			{
				SwingUtilities.invokeLater(this::updateSidebar);
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
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		cacheConfig();
	}

	private void cacheConfig()
	{
		hideOthers = !config.othersShow().isModel();
		hideOwn = !config.ownShow().isModel();
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
