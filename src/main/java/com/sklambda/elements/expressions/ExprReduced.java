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

@Name("Reduced List")
@Description({
		"Folds a whole list down to a single value with a two-argument lambda.",
		"\tThe list is combined pairwise left-to-right: the first argument is the running result (the accumulator), "
				+ "the second is the next element. For `3, 5, 2` and an adding lambda this runs `add(3, 5)` → 8, then "
				+ "`add(8, 2)` → 10.",
		"\tA one-element list reduces to that element (the lambda is never called); an empty list reduces to nothing."
})
@Example("""
		# {_add} = lambda (a: number, b: number) -> number: return {_a} + {_b}
		set {_total} to {_nums::*} reduced with {_add}
		""")
@Since("1.0.0")
public class ExprReduced extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprReduced.class, Object.class)
				.supplier(ExprReduced::new)
				.addPatterns("%objects% reduced (with|using) %object%")
				.build());
	}

	private Expression<?> source;
	private Expression<?> reducer;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		reducer = exprs[1];
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Object[] in = source.getArray(event);
		if (in.length == 0) return null;
		Object accumulator = in[0];
		if (in.length > 1) {
			Lambda lambda = Lambda.from(reducer.getSingle(event));
			if (lambda == null) return null;
			for (int i = 1; i < in.length; i++) {
				accumulator = lambda.invoke(new Object[]{accumulator, in[i]});
			}
		}
		return accumulator == null ? null : new Object[]{accumulator};
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
		return source.toString(event, debug) + " reduced with " + reducer.toString(event, debug);
	}

}
