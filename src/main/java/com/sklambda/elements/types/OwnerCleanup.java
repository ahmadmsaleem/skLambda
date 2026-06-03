package com.sklambda.elements.types;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class OwnerCleanup {

	private OwnerCleanup() {}

	/**
	 * Registers the hooks that drop owned listeners when their owner goes away: a player quitting, an
	 * entity leaving the world (death/despawn), or a chunk/world unloading. Call once at startup.
	 */
	public static void installOwnerCleanup(Plugin plugin) {
		registerCleanupHook(plugin, PlayerQuitEvent.class, evt -> ((PlayerQuitEvent) evt).getPlayer());
		// Players are handled by PlayerQuitEvent alone; ignore their entity removals so a cross-world
		// teleport (which also removes-from-world) doesn't drop their listeners.
		registerCleanupHook(plugin, EntityRemoveFromWorldEvent.class, evt -> {
			Entity entity = ((EntityRemoveFromWorldEvent) evt).getEntity();
			return entity instanceof Player ? null : entity;
		});
		registerCleanupHook(plugin, ChunkUnloadEvent.class, evt -> ((ChunkUnloadEvent) evt).getChunk());
		registerCleanupHook(plugin, WorldUnloadEvent.class, evt -> ((WorldUnloadEvent) evt).getWorld());
	}

	private static void registerCleanupHook(Plugin plugin, Class<? extends Event> eventType,
											 Function<Event, @Nullable Object> ownerOf) {
		org.bukkit.event.Listener holder = new org.bukkit.event.Listener() {};
		EventExecutor executor = (lst, evt) -> ListenerRegistry.unregisterAllOwnedBy(ownerOf.apply(evt));
		Bukkit.getPluginManager().registerEvent(eventType, holder, EventPriority.MONITOR, executor, plugin, true);
	}

	/**
	 * Whether two owner values refer to the same thing: players/entities by UUID, chunks by world +
	 * coordinates, worlds by UID, everything else by {@code equals}.
	 */
	static boolean sameOwner(@Nullable Object a, @Nullable Object b) {
		if (a == null || b == null) return false;
		if (a instanceof OfflinePlayer pa && b instanceof OfflinePlayer pb) return pa.getUniqueId().equals(pb.getUniqueId());
		if (a instanceof Entity ea && b instanceof Entity eb) return ea.getUniqueId().equals(eb.getUniqueId());
		if (a instanceof Chunk ca && b instanceof Chunk cb)
			return ca.getX() == cb.getX() && ca.getZ() == cb.getZ() && ca.getWorld().getUID().equals(cb.getWorld().getUID());
		if (a instanceof World wa && b instanceof World wb) return wa.getUID().equals(wb.getUID());
		return a.equals(b);
	}
}
