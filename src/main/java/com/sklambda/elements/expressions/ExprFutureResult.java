package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Future;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Future Result")
@Description("The resolved value(s) of one or more futures, or nothing for futures that aren't done yet (or that failed). Read it after a `wait for`, when the future is guaranteed resolved.")
@Example("""
		wait for {_f} for at most 5 seconds:
			set {_value} to result of {_f}
		set {_all::*} to results of {_futures::*}
		""")
@Since("1.3.0")
public class ExprFutureResult extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprFutureResult.class, Object.class)
				.supplier(ExprFutureResult::new)
				.addPatterns("[the] result[s] of %futures%")
				.build());
	}

	private Expression<?> futuresExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		futuresExpr = exprs[0];
		return true;
	}

	@Override
	protected Object @NotNull [] get(@NotNull Event event) {
		List<Object> results = new ArrayList<>();
		for (Object value : futuresExpr.getArray(event)) {
			Future future = Future.from(value);
			if (future == null) continue;
			Object result = future.result();
			if (result != null) results.add(result);
		}
		return results.toArray();
	}

	@Override
	public boolean isSingle() {
		return futuresExpr.isSingle();
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "result of " + futuresExpr.toString(event, debug);
	}

}
