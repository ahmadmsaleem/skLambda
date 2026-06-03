package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Partially Applied Lambda")
@Description({
		"Pre-binds the leading arguments of a lambda, returning a smaller-arity lambda that supplies the rest. "
				+ "Binding `5` to a `(a, b)` lambda gives a one-argument lambda that calls the original with `5` as "
				+ "its first argument.",
		"\tThe original's captured scope is kept (it performs the real call), and the new lambda's declared "
				+ "parameters are the leftover ones, so `number of parameters` reads correctly."
})
@Example("""
		set {_add} to lambda (a: number, b: number) -> number: return {_a} + {_b}
		set {_add5} to {_add} with 5 bound
		send "%call lambda {_add5} with 10%"      # 15
		""")
@Since("1.1.0")
public class ExprBoundLambda extends SimpleExpression<Lambda> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprBoundLambda.class, Lambda.class)
				.supplier(ExprBoundLambda::new)
				.addPatterns("%object% with %objects% bound")
				.build());
	}

	private Expression<?> lambdaExpr;
	private Expression<?> argsExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		lambdaExpr = exprs[0];
		argsExpr = LiteralUtils.defendExpression(exprs[1]);
		return LiteralUtils.canInitSafely(argsExpr);
	}

	@Override
	protected Lambda @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(lambdaExpr.getSingle(event));
		if (lambda == null) return new Lambda[0];
		return new Lambda[]{lambda.bind(argsExpr.getArray(event))};
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
		return lambdaExpr.toString(event, debug) + " with " + argsExpr.toString(event, debug) + " bound";
	}

}
