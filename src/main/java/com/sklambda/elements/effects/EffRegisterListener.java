package com.sklambda.elements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Register Listener")
@Description("Activates a listener that was defined with `set %~object% to listener for ...:`. No-op if already active.")
@Example("""
		set {my_listener} to listener for chat:
			on trigger:
				send "%message%" to console
		register {my_listener}
		""")
@Since("0.0.1-alpha")
public class EffRegisterListener extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffRegisterListener.class)
				.supplier(EffRegisterListener::new)
				.addPatterns("register %listener%")
				.build());
	}

	private Expression<?> listenerExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		listenerExpr = exprs[0];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Listener listener = Listener.from(listenerExpr.getSingle(event));
		if (listener != null) listener.register();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "register " + listenerExpr.toString(event, debug);
	}

}
