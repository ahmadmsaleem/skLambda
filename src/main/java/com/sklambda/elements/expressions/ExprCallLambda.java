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

@Name("Call Lambda")
@Description("Calls a lambda, optionally passing arguments, and returns its result.")
@Example("""
		set {_x} to call lambda {_double} with 5
		set {_t} to invoke lambda {_now_ms}
		""")
@Since("0.0.1-alpha")
public class ExprCallLambda extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprCallLambda.class, Object.class)
				.supplier(ExprCallLambda::new)
				.addPatterns(
						"[the] result of calling lambda %object% [with %-objects%]",
						"calling lambda %object% [with %-objects%]",
						"(call|invoke) lambda %object% [with %-objects%]")
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
	protected Object @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(lambdaExpr.getSingle(event));
		if (lambda == null) return null;
		Object[] args = argsExpr != null ? argsExpr.getArray(event) : new Object[0];
		Object result = lambda.invoke(args);
		if (result == null) return null;
		return new Object[]{result};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "call lambda " + lambdaExpr.toString(event, debug)
				+ (argsExpr != null ? " with " + argsExpr.toString(event, debug) : "");
	}

}
