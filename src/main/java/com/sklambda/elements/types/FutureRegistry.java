package com.sklambda.elements.types;

import ch.njol.skript.config.Config;
import ch.njol.skript.config.Node;
import ch.njol.skript.lang.parser.ParserInstance;
import com.sklambda.SkLambda;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks live futures so {@code /sklambda futures} and the leak notifier can report the ones that never
 * settle. Entries are held weakly: a future nobody references is garbage, not a leak, and drops out on its own.
 */
public final class FutureRegistry {

	private static final Map<Future, Boolean> TRACKED =
			Collections.synchronizedMap(new WeakHashMap<>());

	/** Futures already reported by the notifier, so each is warned about once per interval. */
	private static final Map<Future, Long> LAST_WARNED =
			Collections.synchronizedMap(new WeakHashMap<>());

	private FutureRegistry() {}

	/** A "script:line" label for the node currently being parsed, for futures to record as their origin. */
	public static String currentOrigin() {
		Node node = ParserInstance.get().getNode();
		if (node == null) return "unknown";
		Config config = node.getConfig();
		String file = config == null ? "unknown" : config.getFileName();
		int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
		return (slash >= 0 ? file.substring(slash + 1) : file) + ":" + node.getLine();
	}

	static void track(Future future) {
		TRACKED.put(future, Boolean.TRUE);
	}

	/** Every future still reachable, newest last. */
	public static List<Future> all() {
		synchronized (TRACKED) {
			return new ArrayList<>(TRACKED.keySet());
		}
	}

	/** Futures that have not settled, oldest first, which is the order leaks are worth reading in. */
	public static List<Future> pending() {
		List<Future> pending = new ArrayList<>();
		for (Future future : all()) {
			if (!future.isDone()) pending.add(future);
		}
		pending.sort(Comparator.comparingLong(Future::getAgeMillis).reversed());
		return pending;
	}

	/**
	 * Warns once per {@code warnEveryMs} about each pending future older than {@code warnAfterMs}.
	 * Placeholders: {location}, {duration}, {awaiting}.
	 */
	public static void notifierScan(long warnAfterMs, long warnEveryMs, String template) {
		long now = System.currentTimeMillis();
		for (Future future : pending()) {
			long age = future.getAgeMillis();
			if (age < warnAfterMs) continue;
			Long last = LAST_WARNED.get(future);
			if (last != null && now - last < warnEveryMs) continue;
			Plugin plugin = SkLambda.getInstance();
			if (plugin == null) return;
			LAST_WARNED.put(future, now);
			plugin.getLogger().warning(template
					.replace("{location}", future.getOrigin())
					.replace("{duration}", ListenerRegistry.formatDuration(age))
					.replace("{awaiting}", String.valueOf(future.getAwaitingCount())));
		}
	}

}
