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
import java.util.Deque;

public final class Listener implements org.bukkit.event.Listener {

	private static final ThreadLocal<Deque<Listener>> CONTEXT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

	public static @Nullable Listener currentContext() {
		return CONTEXT_STACK.get().peek();
	}

	private final SkriptEvent skriptEvent;
	private final Class<? extends Event>[] eventClasses;
	private final @Nullable Condition filter;
	private final @Nullable Trigger onTrigger;
	private final @Nullable Trigger onCompletion;
	private final @Nullable Trigger onTimeout;
	private final int targetTriggers;
	private final long timeoutTicks;

	private @Nullable Object localsSnapshot;
	private @Nullable Event lastFiredEvent;
	private @Nullable Event registrationEvent;
	private int currentTriggers;
	private @Nullable BukkitTask timeoutTask;
	private boolean active;
	private boolean shouldCancel;

	public Listener(SkriptEvent skriptEvent, Class<? extends Event>[] eventClasses, @Nullable Condition filter,
					@Nullable Trigger onTrigger, @Nullable Trigger onCompletion, @Nullable Trigger onTimeout,
					int targetTriggers, long timeoutTicks) {
		this.skriptEvent = skriptEvent;
		this.eventClasses = eventClasses;
		this.filter = filter;
		this.onTrigger = onTrigger;
		this.onCompletion = onCompletion;
		this.onTimeout = onTimeout;
		this.targetTriggers = targetTriggers;
		this.timeoutTicks = timeoutTicks;
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
		shouldCancel = false;
		currentTriggers = 0;
		lastFiredEvent = null;
		EventExecutor executor = (lst, evt) -> handle(evt);
		for (Class<? extends Event> eventClass : eventClasses) {
			Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin, false);
		}
		if (timeoutTicks > 0) {
			timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::fireTimeout, timeoutTicks);
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

	private void handle(Event event) {
		if (!active || !skriptEvent.check(event)) return;
		if (localsSnapshot != null) Variables.setLocalVariables(event, localsSnapshot);
		try {
			if (filter != null && !filter.check(event)) return;
			if (onTrigger != null) {
				CONTEXT_STACK.get().push(this);
				try {
					onTrigger.execute(event);
				} finally {
					CONTEXT_STACK.get().pop();
				}
				lastFiredEvent = event;
			}
		} finally {
			localsSnapshot = Variables.copyLocalVariables(event);
			Variables.removeLocals(event);
		}

		if (shouldCancel) {
			teardown();
			return;
		}
		if (onTrigger != null) currentTriggers++;
		if (targetTriggers > 0 && currentTriggers >= targetTriggers) fireCompletion(event);
	}

	private void fireCompletion(Event triggerEvent) {
		if (!active) return;
		if (onCompletion != null) runWith(onCompletion, triggerEvent);
		teardown();
	}

	private void fireTimeout() {
		if (!active) return;
		Event eventToUse = lastFiredEvent != null ? lastFiredEvent
				: registrationEvent != null ? registrationEvent
				: new ListenerDetachedEvent();
		if (onTimeout != null) runWith(onTimeout, eventToUse);
		teardown();
	}

	private void runWith(Trigger trigger, Event event) {
		if (localsSnapshot != null) Variables.setLocalVariables(event, localsSnapshot);
		try {
			trigger.execute(event);
			localsSnapshot = Variables.copyLocalVariables(event);
		} finally {
			Variables.removeLocals(event);
		}
	}

	private void teardown() {
		active = false;
		HandlerList.unregisterAll(this);
		if (timeoutTask != null) {
			timeoutTask.cancel();
			timeoutTask = null;
		}
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
