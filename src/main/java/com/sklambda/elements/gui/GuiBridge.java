package com.sklambda.elements.gui;

import ch.njol.skript.aliases.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Reflective access to skript-gui's {@code GUI} type.
 *
 * <p>skLambda does not build against skript-gui, so everything here is looked up by name and the whole
 * module stays dormant when that plugin is absent. The one method that matters,
 * {@code GUI#setItem(Object, ItemStack, boolean, Consumer)}, already takes a plain {@link Consumer},
 * which is exactly what a {@link com.sklambda.elements.types.Lambda} is.
 */
public final class GuiBridge {

	private static final String GUI_CLASS = "io.github.apickledwalrus.skriptgui.gui.GUI";

	private static @Nullable Class<?> guiClass;
	private static @Nullable Method setItem;
	private static boolean resolved;

	private GuiBridge() {}

	/** Whether skript-gui is installed and its GUI type looks the way we expect. */
	public static synchronized boolean isAvailable() {
		if (!resolved) {
			resolved = true;
			if (Bukkit.getPluginManager().getPlugin("skript-gui") != null) {
				try {
					Class<?> found = Class.forName(GUI_CLASS);
					setItem = found.getMethod("setItem", Object.class, ItemStack.class, boolean.class, Consumer.class);
					guiClass = found;
				} catch (ReflectiveOperationException ignored) {
					// A skript-gui too old or too new to have that signature: leave the module off rather
					// than registering syntax that would fail at runtime.
					guiClass = null;
					setItem = null;
				}
			}
		}
		return guiClass != null;
	}

	/** Whether {@code value} is a skript-gui GUI. */
	public static boolean isGui(@Nullable Object value) {
		return isAvailable() && guiClass != null && guiClass.isInstance(value);
	}

	/**
	 * Puts {@code item} in {@code slot} of {@code gui}, with {@code onClick} run when it is clicked.
	 * Returns whether the call went through.
	 */
	public static boolean setItem(Object gui, Object slot, @Nullable ItemType item, boolean removable,
								  @Nullable Consumer<InventoryClickEvent> onClick) {
		if (!isGui(gui) || setItem == null) return false;
		try {
			setItem.invoke(gui, slot, item == null ? null : item.getRandom(), removable, onClick);
			return true;
		} catch (ReflectiveOperationException failed) {
			return false;
		}
	}

}
