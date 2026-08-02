package com.sklambda.elements.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LambdaInvocationEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private @Nullable Object returnValue;
	private Object @NotNull [] args = new Object[0];
	private boolean errored;

	/**
	 * Whether the body errored out. Skript catches runtime errors inside a trigger, logs them, and reports
	 * failure only through {@code Trigger.execute}'s return value, so bodies record it here for the caller.
	 */
	public boolean hasErrored() {
		return errored;
	}

	public void markErrored() {
		this.errored = true;
	}

	public @Nullable Object getReturnValue() {
		return returnValue;
	}

	public void setReturnValue(@Nullable Object value) {
		this.returnValue = value;
	}

	/** The positional arguments this lambda was invoked with. */
	public Object @NotNull [] getArgs() {
		return args;
	}

	public void setArgs(Object @NotNull [] args) {
		this.args = args;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

}
