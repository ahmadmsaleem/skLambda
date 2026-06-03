package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Negated Lambda")
@Description({
		"Inverts a predicate lambda: the result passes exactly when the original does not. Handy with `passes` "
				+ "and the `filtered where` / `count of ... where` list ops, and as the complement of `always()` / `never()`.",
		"\tThe negated lambda keeps the original's parameters and returns a boolean. A lambda that isn't a predicate "
				+ "(null/non-boolean result) counts as not passing, so its negation passes."
})
@Example("""
		set {_is-op} to lambda (p: player): {_p} is op
		set {_not-op} to negated {_is-op}
		set {_visitors::*} to all players filtered where {_not-op} passes
		""")
@Since("1.1.0")
public class ExprNegatedLambda extends SimpleExpression<Lambda> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprNegatedLambda.class, Lambda.class)
				.supplier(ExprNegatedLambda::new)
				.addPatterns("negated %object%", "negation of %object%")
				.build());
	}

	private Expression<?> lambdaExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		lambdaExpr = exprs[0];
		return true;
	}

	@Override
	protected Lambda @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(lambdaExpr.getSingle(event));
		if (lambda == null) return new Lambda[0];
		return new Lambda[]{lambda.negated()};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Lambda> getReturnType() {
		return Lambda.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "negated " + lambdaExpr.toString(event, debug);
	}

}
