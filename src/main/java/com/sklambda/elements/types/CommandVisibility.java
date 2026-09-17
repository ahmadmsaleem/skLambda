package com.sklambda.elements.types;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.variables.Variables;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.script.Script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Decides which commands each player is sent: script-wide rules plus per-player hides. */
public final class CommandVisibility implements Listener {

	private record ShowOnly(Object source, Set<String> names, @Nullable Condition condition,
							@Nullable Object locals, @Nullable Script script) {}

	private record HideWhere(Object source, Lambda predicate, @Nullable Script script) {}

	private static final List<ShowOnly> SHOW_ONLY = new ArrayList<>();
	private static final List<HideWhere> HIDE_WHERE = new ArrayList<>();
	// Player -> hidden name -> script that hid it (null when not from a script).
	private static final Map<UUID, Map<String, Script>> HIDDEN = new HashMap<>();
	private static final Set<Script> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());

	private static final Set<UUID> PENDING = new HashSet<>();
	private static boolean refreshEveryone;
	private static boolean refreshQueued;
	private static @Nullable Plugin plugin;

	private CommandVisibility() {}

	public static void install(Plugin owner) {
		plugin = owner;
		Bukkit.getPluginManager().registerEvents(new CommandVisibility(), owner);
	}

	// HIGH so Skript's own `on send command list` has already run.
	@EventHandler(priority = EventPriority.HIGH)
	public void onSend(PlayerCommandSendEvent event) {
		Player player = event.getPlayer();
		List<ShowOnly> showOnly;
		List<HideWhere> hideWhere;
		Set<String> hidden;
		synchronized (CommandVisibility.class) {
			showOnly = new ArrayList<>(SHOW_ONLY);
			hideWhere = new ArrayList<>(HIDE_WHERE);
			Map<String, Script> names = HIDDEN.get(player.getUniqueId());
			hidden = names == null ? Set.of() : new HashSet<>(names.keySet());
		}
		if (showOnly.isEmpty() && hideWhere.isEmpty() && hidden.isEmpty()) return;

		Set<String> allowed = new HashSet<>();
		for (ShowOnly rule : showOnly) {
			if (applies(rule, event)) allowed.addAll(rule.names());
		}
		event.getCommands().removeIf(label -> {
			String name = normalize(label);
			if (!showOnly.isEmpty() && !allowed.contains(name)) return true;
			if (hidden.contains(name)) return true;
			for (HideWhere rule : hideWhere) {
				if (Boolean.TRUE.equals(rule.predicate().invoke(new Object[]{label, player}))) return true;
			}
			return false;
		});
	}

	private static boolean applies(ShowOnly rule, Event event) {
		Condition condition = rule.condition();
		if (condition == null) return true;
		Object previous = Variables.removeLocals(event);
		if (rule.locals() != null) Variables.setLocalVariables(event, rule.locals());
		try {
			return condition.check(event);
		} finally {
			Variables.removeLocals(event);
			if (previous != null) Variables.setLocalVariables(event, previous);
		}
	}

	public static synchronized void showOnly(Object source, Set<String> names, @Nullable Condition condition,
									  @Nullable Object locals, @Nullable Script script) {
		// The same line run again replaces its rule instead of stacking a copy.
		SHOW_ONLY.removeIf(rule -> rule.source() == source && rule.names().equals(names));
		SHOW_ONLY.add(new ShowOnly(source, names, condition, locals, script));
		watch(script);
		queueRefresh(null);
	}

	public static synchronized void hideWhere(Object source, Lambda predicate, @Nullable Script script) {
		HIDE_WHERE.removeIf(rule -> rule.source() == source);
		HIDE_WHERE.add(new HideWhere(source, predicate, script));
		watch(script);
		queueRefresh(null);
	}

	public static synchronized void hide(Player[] players, Set<String> names, @Nullable Script script) {
		for (Player player : players) {
			Map<String, Script> hidden = HIDDEN.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
			for (String name : names) hidden.put(name, script);
		}
		watch(script);
		queueRefresh(players);
	}

	public static synchronized void show(Player[] players, Set<String> names) {
		for (Player player : players) {
			Map<String, Script> hidden = HIDDEN.get(player.getUniqueId());
			if (hidden == null) continue;
			hidden.keySet().removeAll(names);
			if (hidden.isEmpty()) HIDDEN.remove(player.getUniqueId());
		}
		queueRefresh(players);
	}

	public static synchronized void reset() {
		SHOW_ONLY.clear();
		HIDE_WHERE.clear();
		HIDDEN.clear();
		queueRefresh(null);
	}

	public static synchronized void reset(Player[] players) {
		for (Player player : players) HIDDEN.remove(player.getUniqueId());
		queueRefresh(players);
	}

	public static Set<String> names(Object[] values) {
		Set<String> names = new HashSet<>();
		for (Object value : values) {
			if (value instanceof String text && !text.isBlank()) names.add(normalize(text));
		}
		return names;
	}

	private static String normalize(String name) {
		String trimmed = name.trim();
		if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
		return trimmed.toLowerCase(Locale.ROOT);
	}

	public static @Nullable Script currentScript(ParserInstance parser) {
		return parser.isActive() ? parser.getCurrentScript() : null;
	}

	/** Drops a script's rules and hides when it unloads, so a reload can't stack or strand them. */
	private static void watch(@Nullable Script script) {
		if (script == null || !WATCHED.add(script)) return;
		script.eventRegistry().register(ScriptLoader.ScriptUnloadEvent.class, (parser, unloaded) -> forget(unloaded));
	}

	private static synchronized void forget(Script script) {
		WATCHED.remove(script);
		boolean changed = SHOW_ONLY.removeIf(rule -> rule.script() == script);
		changed |= HIDE_WHERE.removeIf(rule -> rule.script() == script);
		for (Map<String, Script> hidden : HIDDEN.values()) {
			changed |= hidden.values().removeIf(owner -> owner == script);
		}
		HIDDEN.values().removeIf(Map::isEmpty);
		if (changed) queueRefresh(null);
	}

	// Batched to one resend per player on the next tick, so a script setting several rules sends once.
	private static void queueRefresh(Player @Nullable [] players) {
		if (players == null) {
			refreshEveryone = true;
		} else {
			for (Player player : players) PENDING.add(player.getUniqueId());
		}
		Plugin owner = plugin;
		if (refreshQueued || owner == null || !owner.isEnabled()) return;
		refreshQueued = true;
		Bukkit.getScheduler().runTask(owner, CommandVisibility::flushRefresh);
	}

	private static void flushRefresh() {
		boolean everyone;
		Set<UUID> ids;
		synchronized (CommandVisibility.class) {
			everyone = refreshEveryone;
			ids = new HashSet<>(PENDING);
			PENDING.clear();
			refreshEveryone = false;
			refreshQueued = false;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (everyone || ids.contains(player.getUniqueId())) player.updateCommands();
		}
	}

}
