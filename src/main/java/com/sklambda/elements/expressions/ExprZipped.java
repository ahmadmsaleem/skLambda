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

import java.util.ArrayList;
import java.util.List;

@Name("Zipped Lists")
@Description({
		"Walks two lists in lockstep, combining each pair of elements with a two-argument value lambda and "
				+ "returning a new list of the results.",
		"\tThe lambda is invoked once per index with the matching elements as its two arguments. Iteration "
				+ "stops at the shorter list, and results whose lambda returns nothing are dropped."
})
@Example("""
		# {_add} = lambda (a: number, b: number) -> number: return {_a} + {_b}
		set {_sums::*} to (1, 2, 3) zipped with (10, 20, 30) using {_add}
		# 11, 22, 33
		""")
@Since("1.2.0")
public class ExprZipped extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprZipped.class, Object.class)
				.supplier(ExprZipped::new)
				.addPatterns("%objects% zipped with %objects% (with|using|through) %object%")
				.build());
	}

	private Expression<?> first;
	private Expression<?> second;
	private Expression<?> combiner;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		first = LiteralUtils.defendExpression(exprs[0]);
		second = LiteralUtils.defendExpression(exprs[1]);
		combiner = exprs[2];
		return LiteralUtils.canInitSafely(first) && LiteralUtils.canInitSafely(second);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(combiner.getSingle(event));
		if (lambda == null) return new Object[0];
		Object[] a = first.getArray(event);
		Object[] b = second.getArray(event);
		int count = Math.min(a.length, b.length);
		List<Object> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Object result = lambda.invoke(new Object[]{a[i], b[i]});
			if (result != null) out.add(result);
		}
		return out.toArray();
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return first.toString(event, debug) + " zipped with " + second.toString(event, debug)
				+ " using " + combiner.toString(event, debug);
	}

}
