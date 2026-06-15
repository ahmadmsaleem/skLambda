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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Listener implements org.bukkit.event.Listener {

	/** Narrows an arbitrary value to a Listener, or null if it isn't one. */
	public static @Nullable Listener from(@Nullable Object value) {
		return value instanceof Listener listener ? listener : null;
	}

	private final @Nullable SkriptEvent skriptEvent;
	private final Class<? extends Event>[] eventClasses;
	private final List<Condition> filters;
	private final @Nullable Trigger onTrigger;
	private final @Nullable Trigger onCompletion;
	private final @Nullable Trigger onTimeout;
	private final @Nullable Trigger onEnd;
	private final @Nullable Trigger onTick;
	private final @Nullable Trigger onPause;
	private final @Nullable Trigger onResume;
	private final @Nullable Trigger onRegister;
	/** Optional one-shot continuation fired once at teardown with the final locals snapshot; used by `wait for next`. */
	private final @Nullable Consumer<Object> onResolved;
	/** Watcher mode: polled each tick to read the current value; `onChange` fires when it differs from the last. */
	private final @Nullable Function<Event, Object> watchPoller;
	private final @Nullable Trigger onChange;
	/** Edge-triggered watcher: fired on a boolean false->true transition (`onRising`) or true->false (`onFalling`). */
	private final @Nullable Trigger onRising;
	private final @Nullable Trigger onFalling;

	private final String sourceLocation;
	private final String eventLabel;
	private final @Nullable Object owner;
	final long creationId = ListenerRegistry.nextCreationId();
	private long registeredAtMillis = -1;
	private long lastWarnedAtMillis = -1;
	private boolean warnedAsync;

	private int targetTriggers;
	private long initialTimeoutTicks;
	private final long tickIntervalTicks;
	private long cooldownMs;

	private @Nullable Object localsSnapshot;
	private @Nullable Event lastFiredEvent;
	private @Nullable Event registrationEvent;
	private int currentTriggers;
	private long lastTriggerMillis = -1;
	private @Nullable BukkitTask timeoutTask;
	private @Nullable BukkitTask tickTask;
	private long timeoutEndMillis = -1;
	private boolean active;
	private boolean paused;
	private long pausedRemainingMs = -1;
	private boolean shouldCancel;
	private boolean finished;
	private @Nullable EndReason lastEndReason;
	private boolean hasWatchValue;
	private @Nullable Object lastWatchValue;
	private @Nullable Object watchOld;
	private @Nullable Object watchNew;

	private Listener(Builder builder) {
		this.skriptEvent = builder.skriptEvent;
		this.eventClasses = builder.eventClasses;
		this.filters = builder.filters;
		this.onTrigger = builder.onTrigger;
		this.onCompletion = builder.onCompletion;
		this.onTimeout = builder.onTimeout;
		this.onEnd = builder.onEnd;
		this.onTick = builder.onTick;
		this.onPause = builder.onPause;
		this.onResume = builder.onResume;
		this.onRegister = builder.onRegister;
		this.onResolved = builder.onResolved;
		this.watchPoller = builder.watchPoller;
		this.onChange = builder.onChange;
		this.onRising = builder.onRising;
		this.onFalling = builder.onFalling;
		this.targetTriggers = builder.targetTriggers;
		this.initialTimeoutTicks = builder.timeoutTicks;
		this.tickIntervalTicks = builder.tickIntervalTicks;
		this.cooldownMs = builder.cooldownMs;
		this.sourceLocation = builder.sourceLocation;
		this.eventLabel = builder.eventLabel;
		this.owner = builder.owner;
	}

	public static Builder builder(@Nullable SkriptEvent skriptEvent, Class<? extends Event>[] eventClasses) {
		return new Builder(skriptEvent, eventClasses);
	}

	public static final class Builder {
		private final @Nullable SkriptEvent skriptEvent;
		private final Class<? extends Event>[] eventClasses;
		private List<Condition> filters = Collections.emptyList();
		private @Nullable Trigger onTrigger;
		private @Nullable Trigger onCompletion;
		private @Nullable Trigger onTimeout;
		private @Nullable Trigger onEnd;
		private @Nullable Trigger onTick;
		private @Nullable Trigger onPause;
		private @Nullable Trigger onResume;
		private @Nullable Trigger onRegister;
		private @Nullable Consumer<Object> onResolved;
		private @Nullable Function<Event, Object> watchPoller;
		private @Nullable Trigger onChange;
		private @Nullable Trigger onRising;
		private @Nullable Trigger onFalling;
		private int targetTriggers;
		private long timeoutTicks = -1;
		private long tickIntervalTicks;
		private long cooldownMs;
		private String sourceLocation = "unknown";
		private String eventLabel = "";
		private @Nullable Object owner;

		private Builder(@Nullable SkriptEvent skriptEvent, Class<? extends Event>[] eventClasses) {
			this.skriptEvent = skriptEvent;
			this.eventClasses = eventClasses;
		}

		public Builder filters(List<Condition> filters) { this.filters = filters; return this; }
		public Builder onTrigger(@Nullable Trigger onTrigger) { this.onTrigger = onTrigger; return this; }
		public Builder onCompletion(@Nullable Trigger onCompletion) { this.onCompletion = onCompletion; return this; }
		public Builder onTimeout(@Nullable Trigger onTimeout) { this.onTimeout = onTimeout; return this; }
		public Builder onEnd(@Nullable Trigger onEnd) { this.onEnd = onEnd; return this; }
		public Builder onTick(@Nullable Trigger onTick) { this.onTick = onTick; return this; }
		public Builder onPause(@Nullable Trigger onPause) { this.onPause = onPause; return this; }
		public Builder onResume(@Nullable Trigger onResume) { this.onResume = onResume; return this; }
		public Builder onRegister(@Nullable Trigger onRegister) { this.onRegister = onRegister; return this; }
		public Builder onResolved(@Nullable Consumer<Object> onResolved) { this.onResolved = onResolved; return this; }
		public Builder watchPoller(@Nullable Function<Event, Object> watchPoller) { this.watchPoller = watchPoller; return this; }
		public Builder onChange(@Nullable Trigger onChange) { this.onChange = onChange; return this; }
		public Builder onRising(@Nullable Trigger onRising) { this.onRising = onRising; return this; }
		public Builder onFalling(@Nullable Trigger onFalling) { this.onFalling = onFalling; return this; }
		public Builder triggers(int targetTriggers) { this.targetTriggers = targetTriggers; return this; }
		public Builder timeoutTicks(long timeoutTicks) { this.timeoutTicks = timeoutTicks; return this; }
		public Builder tickIntervalTicks(long tickIntervalTicks) { this.tickIntervalTicks = tickIntervalTicks; return this; }
		public Builder cooldownMillis(long cooldownMs) { this.cooldownMs = cooldownMs; return this; }
		public Builder sourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; return this; }
		public Builder eventLabel(String eventLabel) { this.eventLabel = eventLabel; return this; }
		public Builder owner(@Nullable Object owner) { this.owner = owner; return this; }

		public Listener build() {
			return new Listener(this);
		}
	}

	public String getSourceLocation() {
		return sourceLocation;
	}

	public String getEventLabel() {
		return eventLabel;
	}

	public @Nullable Object getOwner() {
		return owner;
	}

	public boolean isOwnedBy(@Nullable Object o) {
		return OwnerCleanup.sameOwner(owner, o);
	}

	/** Why this listener last stopped, readable inside its `on end` callback, or null if it hasn't ended. */
	public synchronized @Nullable EndReason getEndReason() {
		return lastEndReason;
	}

	public synchronized long getAliveMillis() {
		return registeredAtMillis < 0 ? 0 : Math.max(0, System.currentTimeMillis() - registeredAtMillis);
	}

	synchronized void maybeWarn(long now, long warnAfterMs, long warnEveryMs, String template) {
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
				.replace("{duration}", ListenerRegistry.formatDuration(alive));
		plugin.getLogger().warning(message);
	}

	/** Captures locals and the registration event for later replay in detached callbacks. */
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
		lastTriggerMillis = -1;
		lastFiredEvent = null;
		registeredAtMillis = System.currentTimeMillis();
		lastWarnedAtMillis = -1;
		warnedAsync = false;
		ListenerRegistry.add(this);
		EventExecutor executor = (lst, evt) -> handle(evt);
		for (Class<? extends Event> eventClass : eventClasses) {
			Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin, false);
		}
		if (initialTimeoutTicks > 0) {
			scheduleTimeout(initialTimeoutTicks);
		}
		if ((onTick != null || watchPoller != null) && tickIntervalTicks > 0) {
			tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::fireTick, tickIntervalTicks, tickIntervalTicks);
		}
		if (onRegister != null) {
			runWith(onRegister, callbackEvent());
		}
		return true;
	}

	public synchronized boolean unregister() {
		if (!active) return false;
		teardown(EndReason.UNREGISTERED);
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
		if (onPause != null) runWith(onPause, callbackEvent());
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
		if (onResume != null) runWith(onResume, callbackEvent());
		return true;
	}

	private void handle(Event event) {
		if (Bukkit.isPrimaryThread()) {
			handleSync(event);
			return;
		}
		// The event fired off the main thread. Marshal the listener body onto the main thread so
		// user callbacks run where Skript expects them (matching vanilla Skript). The triggering
		// event has already dispatched by the time we run next tick, so the body can't cancel or
		// modify it.
		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return;
		warnAsyncOnce();
		Bukkit.getScheduler().runTask(plugin, () -> handleSync(event));
	}

	private synchronized void warnAsyncOnce() {
		if (warnedAsync) return;
		warnedAsync = true;
		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return;
		plugin.getLogger().warning("Listener at " + sourceLocation + " for '" + eventLabel
				+ "' is handling an event that fires off the main thread; its callbacks run on the"
				+ " main thread the following tick and cannot cancel or modify that event.");
	}

	private synchronized void handleSync(Event event) {
		if (!active || paused || skriptEvent == null || !skriptEvent.check(event)) return;
		Object preexisting = enterScope(event);
		boolean skipped;
		try {
			for (Condition c : filters) {
				if (!c.check(event)) return;
			}
			if (cooldownMs > 0 && lastTriggerMillis >= 0
					&& System.currentTimeMillis() - lastTriggerMillis < cooldownMs) {
				return;
			}
			if (onTrigger != null) {
				ListenerRegistry.SKIP_FLAG.set(Boolean.FALSE);
				currentTriggers++;
				ListenerRegistry.pushContext(this);
				try {
					onTrigger.execute(event);
				} finally {
					ListenerRegistry.popContext();
				}
				skipped = ListenerRegistry.SKIP_FLAG.get();
				ListenerRegistry.SKIP_FLAG.set(Boolean.FALSE);
				if (skipped) {
					currentTriggers--;
				} else {
					lastFiredEvent = event;
					lastTriggerMillis = System.currentTimeMillis();
				}
			}
		} finally {
			captureSnapshot(event);
			exitScope(event, preexisting);
		}

		if (shouldCancel) {
			teardown(EndReason.CANCELLED);
			return;
		}
		if (targetTriggers > 0 && currentTriggers >= targetTriggers) fireCompletion(event);
	}

	private void fireCompletion(Event triggerEvent) {
		if (!active) return;
		if (onCompletion != null) runWith(onCompletion, triggerEvent);
		teardown(EndReason.COMPLETION);
	}

	private synchronized void fireTimeout() {
		if (!active) return;
		if (onTimeout != null) runWith(onTimeout, callbackEvent());
		teardown(EndReason.TIMEOUT);
	}

	private synchronized void fireTick() {
		if (!active || paused) return;
		if (watchPoller != null) {
			pollAndMaybeFire();
			return;
		}
		if (onTick != null) runWith(onTick, callbackEvent());
	}

	/** Watcher tick: re-read the watched value; if it changed since last poll, run `onChange` with old/new bound. */
	private void pollAndMaybeFire() {
		Event event = callbackEvent();
		Object current;
		Object preexisting = enterScope(event);
		try {
			current = watchPoller.apply(event);
			captureSnapshot(event);
		} finally {
			exitScope(event, preexisting);
		}
		if (!hasWatchValue) {
			// First poll establishes the baseline; a change needs a prior value to compare against.
			hasWatchValue = true;
			lastWatchValue = current;
			return;
		}
		if (Objects.equals(current, lastWatchValue)) return;
		watchOld = lastWatchValue;
		watchNew = current;
		lastWatchValue = current;
		if (onChange != null) runWith(onChange, event);
		// Edge-triggered: a boolean transition fires rising (false->true) or falling (true->false).
		boolean wasTrue = Boolean.TRUE.equals(watchOld);
		boolean isTrue = Boolean.TRUE.equals(watchNew);
		if (isTrue && !wasTrue) {
			if (onRising != null) runWith(onRising, event);
		} else if (wasTrue && !isTrue) {
			if (onFalling != null) runWith(onFalling, event);
		}
		if (shouldCancel) teardown(EndReason.CANCELLED);
	}

	/** The value before the most recent change, readable as `old value` inside `on change`. */
	public synchronized @Nullable Object getWatchOld() {
		return watchOld;
	}

	/** The value after the most recent change, readable as `new value` inside `on change`. */
	public synchronized @Nullable Object getWatchNew() {
		return watchNew;
	}

	/**
	 * The event replayed into a detached callback (timeout/tick/end): the last event that triggered,
	 * else the registration event, else a placeholder.
	 */
	private Event callbackEvent() {
		return lastFiredEvent != null ? lastFiredEvent
				: registrationEvent != null ? registrationEvent
				: new ListenerDetachedEvent();
	}

	private void runWith(Trigger trigger, Event event) {
		Object preexisting = enterScope(event);
		ListenerRegistry.pushContext(this);
		try {
			trigger.execute(event);
			captureSnapshot(event);
		} finally {
			ListenerRegistry.popContext();
			exitScope(event, preexisting);
		}
	}

	/** Saves the event's current locals, then replays this listener's snapshot into it. Returns the saved locals. */
	private @Nullable Object enterScope(Event event) {
		Object preexisting = Variables.copyLocalVariables(event);
		if (localsSnapshot != null) Variables.setLocalVariables(event, localsSnapshot);
		return preexisting;
	}

	/** Copies the event's locals back into this listener's snapshot. */
	private void captureSnapshot(Event event) {
		Object updated = Variables.copyLocalVariables(event);
		if (updated != null) localsSnapshot = updated;
	}

	/** Restores the locals saved by {@link #enterScope}. */
	private void exitScope(Event event, @Nullable Object preexisting) {
		if (preexisting != null) {
			Variables.setLocalVariables(event, preexisting);
		} else {
			Variables.removeLocals(event);
		}
	}

	private synchronized void teardown(EndReason reason) {
		if (!active) return;
		// Mark terminated up front so the on end callback sees a stopped listener and a re-entrant
		// unregister/cancel from within it is a no-op.
		active = false;
		finished = true;
		lastEndReason = reason;
		ListenerRegistry.remove(this);
		HandlerList.unregisterAll(this);
		if (timeoutTask != null) {
			timeoutTask.cancel();
			timeoutTask = null;
		}
		if (tickTask != null) {
			tickTask.cancel();
			tickTask = null;
		}
		timeoutEndMillis = -1;
		registeredAtMillis = -1;
		if (onEnd != null) {
			runWith(onEnd, callbackEvent());
		}
		paused = false;
		pausedRemainingMs = -1;
		// Resume any awaiting trigger last, after callbacks have settled the locals snapshot.
		if (onResolved != null) onResolved.accept(localsSnapshot);
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

	// ---- cooldown (debounce window) ----

	public synchronized long getCooldownMillis() {
		return cooldownMs;
	}

	public synchronized void setCooldownMillis(long ms) {
		cooldownMs = Math.max(0, ms);
	}

	public synchronized void addCooldownMillis(long deltaMs) {
		cooldownMs = Math.max(0, cooldownMs + deltaMs);
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
		String subject = watchPoller != null ? "watcher" + (eventLabel.isEmpty() ? "" : " on " + eventLabel)
				: "listener for " + skriptEvent;
		return subject + (active ? " (active)" : " (inactive)");
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
