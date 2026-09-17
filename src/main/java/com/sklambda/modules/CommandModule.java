package com.sklambda.modules;

import com.sklambda.elements.effects.EffHideCommandsWhere;
import com.sklambda.elements.effects.EffPlayerCommands;
import com.sklambda.elements.effects.EffResetCommandVisibility;
import com.sklambda.elements.effects.EffShowOnlyCommands;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;

public final class CommandModule implements AddonModule {

	@Override
	public @NotNull String name() {
		return "commands";
	}

	@Override
	public void load(@NotNull SkriptAddon addon) {
		SyntaxRegistry registry = addon.syntaxRegistry();
		EffShowOnlyCommands.register(registry);
		EffHideCommandsWhere.register(registry);
		EffPlayerCommands.register(registry);
		EffResetCommandVisibility.register(registry);
	}

}
