package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.variables.Variables;
import com.sklambda.SkLambda;
import com.sklambda.elements.events.ListenerDetachedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class Listener implements org.bukkit.event.Listener {

	private static final ThreadLocal<Deque<Listener>> CONTEXT_STACK = ThreadLocal.withInitial(ArrayDeque::new);
	public static final ThreadLocal<Boolean> SKIP_FLAG = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/** All currently-active listeners, in registration order. */
	private static final Set<Listener> ACTIVE = Collections.synchronizedSet(new LinkedHashSet<>());
	private static final AtomicLong CREATION_COUNTER = new AtomicLong();

	public static @Nullable Listener currentContext() {
		return CONTEXT_STACK.get().peek();
	}

	/** A snapshot of the active listeners, in registration order. */
	public static List<Listener> activeListeners() {
		synchronized (ACTIVE) {
			return new ArrayList<>(ACTIVE);
		}
	}

	/** Unregisters every active listener; returns how many were stopped. */
	public static int unregisterAll() {
		int count = 0;
		for (Listener listener : activeListeners()) {
			if (listener.unregister()) count++;
		}
		return count;
	}

	/** The most recently created listener that is still active, or null if none are. */
	public static @Nullable Listener lastCreated() {
		synchronized (ACTIVE) {
			Listener latest = null;
			for (Listener listener : ACTIVE) {
				if (latest == null || listener.creationId > latest.creationId) latest = listener;
			}
			return latest;
		}
	}

	/** Warns (once per {@code warnEveryMs}) about every active listener alive longer than {@code warnAfterMs}. */
	public static void notifierScan(long warnAfterMs, long warnEveryMs, String template) {
		long now = System.currentTimeMillis();
		for (Listener listener : activeListeners()) {
			listener.maybeWarn(now, warnAfterMs, warnEveryMs, template);
		}
	}

	/** Formats a duration in milliseconds as a compact "1h 2m 3s" string. */
	public static String formatDuration(long ms) {
		long totalSeconds = Math.max(0, ms) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		StringBuilder sb = new StringBuilder();
		if (hours > 0) sb.append(hours).append("h ");
		if (hours > 0 || minutes > 0) sb.append(minutes).append("m ");
		return sb.append(seconds).append('s').toString();
	}

	/** Narrows an arbitrary value to a Listener, or null if it isn't one. */
	public static @Nullable Listener from(@Nullable Object value) {
		return value instanceof Listener listener ? listener : null;
	}

	private final SkriptEvent skriptEvent;
	private final Class<? extends Event>[] eventClasses;
	private final List<Condition> filters;
	private final @Nullable Trigger onTrigger;
	private final @Nullable Trigger onCompletion;
	private final @Nullable Trigger onTimeout;

	private final String sourceLocation;
	private final String eventLabel;
	private final long creationId = CREATION_COUNTER.incrementAndGet();
	private long registeredAtMillis = -1;
	private long lastWarnedAtMillis = -1;

	private int targetTriggers;
	private long initialTimeoutTicks;

	private @Nullable Object localsSnapshot;
	private @Nullable Event lastFiredEvent;
	private @Nullable Event registrationEvent;
	private int currentTriggers;
	private @Nullable BukkitTask timeoutTask;
	private long timeoutEndMillis = -1;
	private boolean active;
	private boolean paused;
	private long pausedRemainingMs = -1;
	private boolean shouldCancel;
	private boolean finished;

	public Listener(SkriptEvent skriptEvent, Class<? extends Event>[] eventClasses, List<Condition> filters,
					@Nullable Trigger onTrigger, @Nullable Trigger onCompletion, @Nullable Trigger onTimeout,
					int targetTriggers, long timeoutTicks, String sourceLocation, String eventLabel) {
		this.skriptEvent = skriptEvent;
		this.eventClasses = eventClasses;
		this.filters = filters;
		this.onTrigger = onTrigger;
		this.onCompletion = onCompletion;
		this.onTimeout = onTimeout;
		this.targetTriggers = targetTriggers;
		this.initialTimeoutTicks = timeoutTicks;
		this.sourceLocation = sourceLocation;
		this.eventLabel = eventLabel;
	}

	public String getSourceLocation() {
		return sourceLocation;
	}

	public String getEventLabel() {
		return eventLabel;
	}

	/** How long this listener has been registered, in milliseconds, or 0 if inactive. */
	public synchronized long getAliveMillis() {
		return registeredAtMillis < 0 ? 0 : Math.max(0, System.currentTimeMillis() - registeredAtMillis);
	}

	private synchronized void maybeWarn(long now, long warnAfterMs, long warnEveryMs, String template) {
		if (!active || registeredAtMillis < 0) return;
		long alive = now - registeredAtMillis;
		if (alive < warnAfterMs) return;
		if (lastWarnedAtMillis >= 0 && now - lastWarnedAtMillis < warnEveryMs) return;
		lastWarnedAtMillis = now;
		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return;
		String message = template
				.replace("{location}", sourceLocation)
				.replace("{event}", eventLabel)
				.replace("{duration}", formatDuration(alive));
		plugin.getLogger().warning(message);
	}

	/** Captures locals and the registration event for later replay in completion/timeout. */
	public void captureFrom(Event event) {
		localsSnapshot = Variables.copyLocalVariables(event);
		registrationEvent = event;
	}

	public synchronized boolean register() {
		if (active) return false;
		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return false;
		active = true;
		paused = false;
		pausedRemainingMs = -1;
		shouldCancel = false;
		finished = false;
		currentTriggers = 0;
		lastFiredEvent = null;
		registeredAtMillis = System.currentTimeMillis();
		lastWarnedAtMillis = -1;
		ACTIVE.add(this);
		EventExecutor executor = (lst, evt) -> handle(evt);
		for (Class<? extends Event> eventClass : eventClasses) {
			Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin, false);
		}
		if (initialTimeoutTicks > 0) {
			scheduleTimeout(initialTimeoutTicks);
		}
		return true;
	}

	public synchronized boolean unregister() {
		if (!active) return false;
		teardown();
		return true;
	}

	public void markCancel() {
		shouldCancel = true;
	}

	public synchronized boolean isActive() {
		return active;
	}

	public synchronized boolean isPaused() {
		return active && paused;
	}

	public synchronized boolean pause() {
		if (!active || paused) return false;
		paused = true;
		if (timeoutEndMillis > 0) {
			pausedRemainingMs = Math.max(0, timeoutEndMillis - System.currentTimeMillis());
		} else {
			pausedRemainingMs = -1;
		}
		if (timeoutTask != null) {
			timeoutTask.cancel();
			timeoutTask = null;
		}
		timeoutEndMillis = -1;
		return true;
	}

	public synchronized boolean resume() {
		if (!active || !paused) return false;
		paused = false;
		if (pausedRemainingMs >= 0) {
			long ticks = Math.max(1, pausedRemainingMs / 50);
			scheduleTimeout(ticks);
		}
		pausedRemainingMs = -1;
		return true;
	}

	private synchronized void handle(Event event) {
		if (!active || paused || !skriptEvent.check(event)) return;
		Object preexisting = Variables.copyLocalVariables(event);
		if (localsSnapshot != null) Variables.setLocalVariables(event, localsSnapshot);
		boolean skipped = false;
		try {
			for (Condition c : filters) {
				if (!c.check(event)) return;
			}
			if (onTrigger != null) {
				SKIP_FLAG.set(Boolean.FALSE);
				currentTriggers++;
				CONTEXT_STACK.get().push(this);
				try {
					onTrigger.execute(event);
				} finally {
					CONTEXT_STACK.get().pop();
				}
				skipped = SKIP_FLAG.get();
				SKIP_FLAG.set(Boolean.FALSE);
				if (skipped) {
					currentTriggers--;
				} else {
					lastFiredEvent = event;
				}
			}
		} finally {
			Object updated = Variables.copyLocalVariables(event);
			if (updated != null) localsSnapshot = updated;
			if (preexisting != null) {
				Variables.setLocalVariables(event, preexisting);
			} else {
				Variables.removeLocals(event);
			}
		}

		if (shouldCancel) {
			teardown();
			return;
		}
		if (targetTriggers > 0 && currentTriggers >= targetTriggers) fireCompletion(event);
	}

	private void fireCompletion(Event triggerEvent) {
		if (!active) return;
		if (onCompletion != null) runWith(onCompletion, triggerEvent);
		teardown();
	}

	private synchronized void fireTimeout() {
		if (!active) return;
		Event eventToUse = lastFiredEvent != null ? lastFiredEvent
				: registrationEvent != null ? registrationEvent
				: new ListenerDetachedEvent();
		if (onTimeout != null) runWith(onTimeout, eventToUse);
		teardown();
	}

	private void runWith(Trigger trigger, Event event) {
		Object preexisting = Variables.copyLocalVariables(event);
		if (localsSnapshot != null) Variables.setLocalVariables(event, localsSnapshot);
		CONTEXT_STACK.get().push(this);
		try {
			trigger.execute(event);
			Object updated = Variables.copyLocalVariables(event);
			if (updated != null) localsSnapshot = updated;
		} finally {
			CONTEXT_STACK.get().pop();
			if (preexisting != null) {
				Variables.setLocalVariables(event, preexisting);
			} else {
				Variables.removeLocals(event);
			}
		}
	}

	private void teardown() {
		active = false;
		paused = false;
		finished = true;
		pausedRemainingMs = -1;
		registeredAtMillis = -1;
		ACTIVE.remove(this);
		HandlerList.unregisterAll(this);
		if (timeoutTask != null) {
			timeoutTask.cancel();
			timeoutTask = null;
		}
		timeoutEndMillis = -1;
	}

	private void scheduleTimeout(long ticks) {
		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return;
		long clamped = Math.max(1, ticks);
		timeoutEndMillis = System.currentTimeMillis() + clamped * 50L;
		timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::fireTimeout, clamped);
	}

	// ---- triggers (remaining count) ----

	public synchronized int getRemainingTriggers() {
		if (targetTriggers <= 0) return 0;
		return Math.max(0, targetTriggers - currentTriggers);
	}

	public synchronized void addTriggers(int delta) {
		if (delta == 0) return;
		targetTriggers = Math.max(0, targetTriggers + delta);
	}

	public synchronized void setRemainingTriggers(int remaining) {
		targetTriggers = currentTriggers + Math.max(0, remaining);
	}

	// ---- countdown (remaining time) ----

	public synchronized long getRemainingCountdownMillis() {
		if (!active) {
			if (finished) return 0;
			return initialTimeoutTicks > 0 ? initialTimeoutTicks * 50L : 0;
		}
		if (paused) return Math.max(0, pausedRemainingMs);
		if (timeoutEndMillis < 0) return 0;
		return Math.max(0, timeoutEndMillis - System.currentTimeMillis());
	}

	public synchronized void addCountdownMillis(long deltaMs) {
		if (deltaMs == 0) return;
		if (!active) {
			initialTimeoutTicks = Math.max(0, initialTimeoutTicks + deltaMs / 50);
			return;
		}
		long newEnd;
		if (timeoutEndMillis < 0) {
			if (deltaMs <= 0) return;
			newEnd = System.currentTimeMillis() + deltaMs;
		} else {
			newEnd = timeoutEndMillis + deltaMs;
		}
		rescheduleTo(newEnd);
	}

	public synchronized void setCountdownMillis(long ms) {
		long clamped = Math.max(0, ms);
		if (!active) {
			initialTimeoutTicks = clamped / 50;
			return;
		}
		rescheduleTo(System.currentTimeMillis() + clamped);
	}

	private void rescheduleTo(long endMillis) {
		if (timeoutTask != null) {
			timeoutTask.cancel();
			timeoutTask = null;
		}
		timeoutEndMillis = endMillis;
		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return;
		long remainingMs = endMillis - System.currentTimeMillis();
		long ticks = Math.max(1, remainingMs / 50);
		timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::fireTimeout, ticks);
	}

	@Override
	public String toString() {
		return "listener for " + skriptEvent + (active ? " (active)" : " (inactive)");
	}

	public static void registerType() {
		Classes.registerClass(new ClassInfo<>(Listener.class, "listener")
				.user("listeners?")
				.name("Listener")
				.description("A declared event listener that can be registered or unregistered at runtime.")
				.since("0.0.1-alpha")
				.parser(new Parser<>() {
					@Override
					public boolean canParse(@NotNull ParseContext context) {
						return false;
					}

					@Override
					public @NotNull String toString(Listener listener, int flags) {
						return listener.toString();
					}

					@Override
					public @NotNull String toVariableNameString(Listener listener) {
						return listener.toString();
					}
				}));
	}

}
