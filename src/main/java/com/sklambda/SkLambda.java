package com.sklambda;

import ch.njol.skript.Skript;
import org.bstats.bukkit.Metrics;
import ch.njol.skript.util.Version;
import com.sklambda.elements.conditions.CondListenerState;
import com.sklambda.elements.effects.EffCancelListener;
import com.sklambda.elements.effects.EffPauseListener;
import com.sklambda.elements.effects.EffRegisterListener;
import com.sklambda.elements.effects.EffRunLambda;
import com.sklambda.elements.effects.EffSkipTrigger;
import com.sklambda.elements.effects.EffUnregisterListener;
import com.sklambda.elements.expressions.ExprCallLambda;
import com.sklambda.elements.expressions.ExprListenerCountdown;
import com.sklambda.elements.expressions.ExprListenerTriggers;
import com.sklambda.elements.sections.SecLambdaDefine;
import com.sklambda.elements.sections.SecListen;
import com.sklambda.elements.types.Lambda;
import com.sklambda.elements.types.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;

public class SkLambda extends JavaPlugin implements AddonModule {

	private static @Nullable SkLambda instance;
	int pluginId = 31630;

	public static @Nullable SkLambda getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		Plugin skript = getServer().getPluginManager().getPlugin("Skript");
		if (skript == null || !skript.isEnabled()) {
			getLogger().severe("Could not find Skript! Make sure you have it installed and that it properly loaded. Disabling...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		} else if (Skript.getVersion().isSmallerThan(new Version("2.14.3"))) {
			getLogger().severe("You are running an unsupported version of Skript. Please update to at least Skript 2.14.3. Disabling...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		instance = this;

		new Metrics(this, pluginId);

		SkriptAddon addon = Skript.instance().registerAddon(SkLambda.class, "skLambda");
		addon.localizer().setSourceDirectories("lang", null);
		addon.loadModules(this);


	}

	@Override
	public @NotNull String name() {
		return "skLambda";
	}

	@Override
	public void init(@NotNull SkriptAddon addon) {
		Lambda.register();
		Listener.registerType();
	}

	@Override
	public void load(@NotNull SkriptAddon addon) {
		SyntaxRegistry registry = addon.syntaxRegistry();
		SecLambdaDefine.register(registry);
		ExprCallLambda.register(registry);
		EffRunLambda.register(registry);

		SecListen.register(registry);
		EffCancelListener.register(registry);
		EffRegisterListener.register(registry);
		EffUnregisterListener.register(registry);
		EffSkipTrigger.register(registry);
		EffPauseListener.register(registry);
		CondListenerState.register(registry);
		ExprListenerTriggers.register(registry);
		ExprListenerCountdown.register(registry);
	}

}
