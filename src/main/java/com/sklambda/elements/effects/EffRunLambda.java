package com.sklambda.elements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Run Lambda")
@Description("Runs a lambda, ignoring any return value.")
@Example("""
		run lambda {_greet} with player
		run lambda {_log_action}
		""")
@Since("0.0.1-alpha")
public class EffRunLambda extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffRunLambda.class)
				.supplier(EffRunLambda::new)
				.addPatterns("run lambda %object% [with %-objects%]")
				.build());
	}

	private Expression<?> lambdaExpr;
	private @Nullable Expression<?> argsExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		lambdaExpr = exprs[0];
		if (exprs[1] != null) {
			argsExpr = LiteralUtils.defendExpression(exprs[1]);
			return LiteralUtils.canInitSafely(argsExpr);
		}
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Object value = lambdaExpr.getSingle(event);
		if (!(value instanceof Lambda lambda)) return;
		Object[] args = argsExpr != null ? argsExpr.getArray(event) : new Object[0];
		lambda.invoke(args);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "run lambda " + lambdaExpr.toString(event, debug)
				+ (argsExpr != null ? " with " + argsExpr.toString(event, debug) : "");
	}

}
