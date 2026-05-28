package com.sklambda.elements.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LambdaInvocationEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private @Nullable Object returnValue;

	public @Nullable Object getReturnValue() {
		return returnValue;
	}

	public void setReturnValue(@Nullable Object value) {
		this.returnValue = value;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

}
