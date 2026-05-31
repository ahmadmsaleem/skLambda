package com.sklambda.elements.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Carrier event for `on completion` / `on timeout` / `on end` bodies that fire outside any Bukkit event context.
 */
public final class ListenerDetachedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

}
