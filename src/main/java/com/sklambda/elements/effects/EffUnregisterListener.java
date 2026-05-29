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

@Name("Unregister Listener")
@Description("Stops an active listener silently (no `on completion` or `on timeout` fires). No-op if already inactive.")
@Example("""
		unregister {my_listener}
		""")
@Since("0.0.1-alpha")
public class EffUnregisterListener extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffUnregisterListener.class)
				.supplier(EffUnregisterListener::new)
				.addPatterns("unregister %listener%")
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
		if (listener != null) listener.unregister();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "unregister " + listenerExpr.toString(event, debug);
	}

}
