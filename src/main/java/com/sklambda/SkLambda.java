package com.sklambda;

import ch.njol.skript.Skript;
import org.bstats.bukkit.Metrics;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.util.Timespan.TimePeriod;
import ch.njol.skript.util.Version;
import com.sklambda.elements.types.Future;
import com.sklambda.elements.types.FutureRegistry;
import com.sklambda.elements.types.ListenerRegistry;
import com.sklambda.elements.types.OwnerCleanup;
import com.sklambda.modules.LambdaModule;
import com.sklambda.modules.ListenerModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import java.util.ArrayList;
import java.util.List;

public class SkLambda extends JavaPlugin {

	private static final int PLUGIN_ID = 31630;

	private static @Nullable SkLambda instance;

	private boolean lambdaEnabled;
	private boolean listenerEnabled;
	private boolean updateNotifications;
	private @Nullable BukkitTask notifierTask;

	public static @Nullable SkLambda getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		Plugin skript = getServer().getPluginManager().getPlugin("Skript");
		if (skript == null || !skript.isEnabled()) {
			getLogger().severe("Could not find Skript! Make sure you have it installed and that it properly loaded. Disabling...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		} else if (Skript.getVersion().isSmallerThan(new Version("2.15.0"))) {
			getLogger().severe("You are running an unsupported version of Skript. Please update to at least Skript 2.15.0. Disabling...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		instance = this;

		saveDefaultConfig();
		lambdaEnabled = getConfig().getBoolean("lambda", true);
		listenerEnabled = getConfig().getBoolean("listener", true);
		updateNotifications = getConfig().getBoolean("update-notifications", true);
		if (!lambdaEnabled && !listenerEnabled) {
			getLogger().warning("Both 'lambda' and 'listener' are disabled in config.yml .");
		}

		new Metrics(this, PLUGIN_ID);

		SkriptAddon addon = Skript.instance().registerAddon(SkLambda.class, "skLambda");
		addon.localizer().setSourceDirectories("lang", null);

		List<AddonModule> modules = new ArrayList<>();
		if (lambdaEnabled) modules.add(new LambdaModule());
		if (listenerEnabled) modules.add(new ListenerModule());
		addon.loadModules(modules.toArray(new AddonModule[0]));

		PluginCommand command = getCommand("sklambda");
		if (command != null) {
			SkLambdaCommand handler = new SkLambdaCommand(this);
			command.setExecutor(handler);
			command.setTabCompleter(handler);
		}

		if (listenerEnabled) {
			OwnerCleanup.installOwnerCleanup(this);
		}
		startNotifiers();

		if (updateNotifications) new UpdateChecker(this);
	}

	@Override
	public void onDisable() {
		Future.shutdownPool();
	}

	/**
	 * Re-reads config.yml and restarts the notifier tasks with the new thresholds. Feature toggles
	 * (lambda/listener) are NOT re-applied: syntax is registered with Skript once at startup.
	 */
	public void reloadSettings() {
		reloadConfig();
		updateNotifications = getConfig().getBoolean("update-notifications", true);
		if (notifierTask != null) {
			notifierTask.cancel();
			notifierTask = null;
		}
		startNotifiers();
	}

	/**
	 * Starts the leak-detection task: periodically warns in the console about listeners and futures that
	 * have outlived their configured threshold. No-op unless enabled in config.
	 */
	private void startNotifiers() {
		if (!getConfig().getBoolean("notifier.enabled", false)) return;

		long warnAfterMs = configMillis("notifier.warn-after", "5 minutes");
		long warnEveryMs = configMillis("notifier.warn-every", "1 minute");
		if (warnAfterMs <= 0 || warnEveryMs <= 0) {
			getLogger().warning("notifier.warn-after / notifier.warn-every must be positive timespans; the notifier is disabled.");
			return;
		}
		String message = getConfig().getString("notifier.message",
				"[skLambda] Listener created in {location} has been alive for {duration}.");

		boolean watchListeners = listenerEnabled && getConfig().getBoolean("notifier.listeners", true);
		boolean watchFutures = getConfig().getBoolean("notifier.futures.enabled", true);
		long futureAfterMs = configMillis("notifier.futures.warn-after", "5 minutes");
		String futureMessage = getConfig().getString("notifier.futures.message",
				"[skLambda] Future created in {location} has been pending for {duration} ({awaiting} awaiting).");

		long periodTicks = Math.max(1, Math.min(warnEveryMs, warnAfterMs) / 50);
		notifierTask = getServer().getScheduler().runTaskTimer(this, () -> {
			if (watchListeners) ListenerRegistry.notifierScan(warnAfterMs, warnEveryMs, message);
			if (watchFutures) FutureRegistry.notifierScan(futureAfterMs, warnEveryMs, futureMessage);
		}, periodTicks, periodTicks);
	}

	/** Reads a config string as a timespan and returns it in milliseconds, falling back to {@code def}. */
	private long configMillis(String path, String def) {
		String raw = getConfig().getString(path, def);
		Timespan ts = Timespan.parse(raw == null ? def : raw);
		if (ts == null) {
			getLogger().warning("Could not read '" + path + "' (\"" + raw + "\") as a timespan; using " + def + ".");
			ts = Timespan.parse(def);
		}
		return ts == null ? 0 : ts.getAs(TimePeriod.MILLISECOND);
	}

}
