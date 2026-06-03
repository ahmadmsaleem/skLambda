package com.sklambda.modules;

import com.sklambda.elements.conditions.CondListenerState;
import com.sklambda.elements.effects.EffCancelListener;
import com.sklambda.elements.effects.EffManageListeners;
import com.sklambda.elements.effects.EffPauseListener;
import com.sklambda.elements.effects.EffRegisterListener;
import com.sklambda.elements.effects.EffSkipTrigger;
import com.sklambda.elements.effects.EffUnregisterListener;
import com.sklambda.elements.expressions.ExprEndReason;
import com.sklambda.elements.expressions.ExprListenerCountdown;
import com.sklambda.elements.expressions.ExprListenerTriggers;
import com.sklambda.elements.expressions.ExprListeners;
import com.sklambda.elements.sections.SecListen;
import com.sklambda.elements.types.EndReason;
import com.sklambda.elements.types.Listener;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;

public final class ListenerModule implements AddonModule {

	@Override
	public @NotNull String name() {
		return "listener";
	}

	@Override
	public void init(@NotNull SkriptAddon addon) {
		Listener.registerType();
		EndReason.register();
	}

	@Override
	public void load(@NotNull SkriptAddon addon) {
		SyntaxRegistry registry = addon.syntaxRegistry();
		SecListen.register(registry);
		EffCancelListener.register(registry);
		EffRegisterListener.register(registry);
		EffUnregisterListener.register(registry);
		EffSkipTrigger.register(registry);
		EffPauseListener.register(registry);
		EffManageListeners.register(registry);
		CondListenerState.register(registry);
		ExprListenerTriggers.register(registry);
		ExprListenerCountdown.register(registry);
		ExprEndReason.register(registry);
		ExprListeners.register(registry);
	}

}
