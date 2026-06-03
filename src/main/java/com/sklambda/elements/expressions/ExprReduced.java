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
		"\tWithout a seed, a one-element list reduces to that element (the lambda is never called) and an empty list "
				+ "reduces to nothing. With `from %object%`, that value seeds the accumulator: the lambda runs for every "
				+ "element (`add(seed, first)` first), an empty list reduces to the seed, and the seed's type can differ "
				+ "from the elements' (e.g. fold a list of items into a text)."
})
@Example("""
		# {_add} = lambda (a: number, b: number) -> number: return {_a} + {_b}
		set {_total} to {_nums::*} reduced with {_add}
		set {_total} to {_nums::*} reduced with {_add} from 100   # 100 + every element
		""")
@Since("1.0.0")
public class ExprReduced extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprReduced.class, Object.class)
				.supplier(ExprReduced::new)
				.addPatterns(
						// Seeded form first: a bare `%object%` reducer would otherwise greedily swallow
						// the trailing `from %object%`, so the more specific pattern must be tried first.
						"%objects% reduced (with|using) %object% from %object%",
						"%objects% reduced (with|using) %object%")
				.build());
	}

	private Expression<?> source;
	private Expression<?> reducer;
	private @Nullable Expression<?> seed;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		reducer = exprs[1];
		if (matchedPattern == 0) seed = LiteralUtils.defendExpression(exprs[2]);
		return LiteralUtils.canInitSafely(source) && (seed == null || LiteralUtils.canInitSafely(seed));
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Object[] in = source.getArray(event);
		Object accumulator;
		int start;
		if (seed != null) {
			accumulator = seed.getSingle(event);
			start = 0;
		} else {
			if (in.length == 0) return null;
			accumulator = in[0];
			start = 1;
		}
		if (start < in.length) {
			Lambda lambda = Lambda.from(reducer.getSingle(event));
			if (lambda == null) return null;
			for (int i = start; i < in.length; i++) {
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
		return source.toString(event, debug) + " reduced with " + reducer.toString(event, debug)
				+ (seed != null ? " from " + seed.toString(event, debug) : "");
	}

}
