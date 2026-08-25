package com.sklambda.modules;

import com.sklambda.elements.gui.EffFillGui;
import com.sklambda.elements.gui.EffFormatGuiSlot;
import com.sklambda.elements.gui.GuiBridge;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;

/** Syntax that only makes sense with skript-gui installed; it registers nothing when that plugin is absent. */
public final class GuiModule implements AddonModule {

	@Override
	public @NotNull String name() {
		return "gui";
	}

	@Override
	public boolean canLoad(@NotNull SkriptAddon addon) {
		return GuiBridge.isAvailable();
	}

	@Override
	public void load(@NotNull SkriptAddon addon) {
		SyntaxRegistry registry = addon.syntaxRegistry();
		EffFormatGuiSlot.register(registry);
		EffFillGui.register(registry);
	}

}
