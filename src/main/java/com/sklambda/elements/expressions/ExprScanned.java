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

@Name("Scanned List")
@Description({
		"Like `reduced`, but keeps every intermediate result instead of just the final one, so it's handy for "
				+ "cumulative totals.",
		"\tThe list is combined pairwise left-to-right with a two-argument lambda (accumulator, next element), and "
				+ "each running result is emitted. For `3, 5, 2` and an adding lambda this yields `3, 8, 10`.",
		"\tWithout a seed, a one-element list scans to that element and an empty list scans to nothing. With "
				+ "`from %object%`, that value seeds the accumulator and is itself the first result, so `3, 5, 2` "
				+ "from 100 yields `100, 103, 108, 110` and an empty list scans to just the seed."
})
@Example("""
		# {_add} = lambda (a: number, b: number) -> number: return {_a} + {_b}
		set {_totals::*} to {_nums::*} scanned with {_add}
		set {_totals::*} to {_nums::*} scanned with {_add} from 100   # 100, then each running total
		""")
@Since("1.2.0")
public class ExprScanned extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprScanned.class, Object.class)
				.supplier(ExprScanned::new)
				.addPatterns(
						"%objects% scanned (with|using) %object% from %object%",
						"%objects% scanned (with|using) %object%")
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
		List<Object> out = new ArrayList<>();
		Object accumulator;
		int start;
		if (seed != null) {
			accumulator = seed.getSingle(event);
			if (accumulator != null) out.add(accumulator);
			start = 0;
		} else {
			if (in.length == 0) return out.toArray();
			accumulator = in[0];
			if (accumulator != null) out.add(accumulator);
			start = 1;
		}
		if (start < in.length) {
			Lambda lambda = Lambda.from(reducer.getSingle(event));
			if (lambda == null) return out.toArray();
			for (int i = start; i < in.length; i++) {
				accumulator = lambda.invoke(new Object[]{accumulator, in[i]});
				if (accumulator != null) out.add(accumulator);
			}
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
		return source.toString(event, debug) + " scanned with " + reducer.toString(event, debug)
				+ (seed != null ? " from " + seed.toString(event, debug) : "");
	}

}
