package com.sklambda.elements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Future;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Complete Future")
@Description("Resolves one or more futures with a value (or nothing), waking any trigger waiting on them. A future that is already resolved is left unchanged.")
@Example("""
		set {_f} to a new future
		complete {_f} with "done"
		complete {_pending::*} with 0
		""")
@Since("1.3.0")
public class EffCompleteFuture extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffCompleteFuture.class)
				.supplier(EffCompleteFuture::new)
				.addPatterns(
						"complete %futures% with %object%",
						"(complete|resolve) %futures%")
				.build());
	}

	private Expression<?> futuresExpr;
	private @Nullable Expression<?> valueExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		futuresExpr = exprs[0];
		if (matchedPattern == 0) valueExpr = exprs[1];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Object value = valueExpr != null ? valueExpr.getSingle(event) : null;
		for (Object candidate : futuresExpr.getArray(event)) {
			Future future = Future.from(candidate);
			if (future != null) future.complete(value);
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "complete " + futuresExpr.toString(event, debug)
				+ (valueExpr != null ? " with " + valueExpr.toString(event, debug) : "");
	}

}
