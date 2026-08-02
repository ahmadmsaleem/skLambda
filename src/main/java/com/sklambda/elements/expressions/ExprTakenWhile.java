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

import java.util.Arrays;

@Name("Taken/Dropped While")
@Description({
		"Splits a list at the FIRST element that fails a predicate. Unlike a filter these depend on position, "
				+ "which is why Skript's `%objects% where [...]` cannot express them.",
		"\t`taken while` keeps the leading run of elements that pass, and stops at the first that doesn't.",
		"\t`dropped while` is the complement: everything from that first failing element onward.",
		"\tTogether they always partition the list: `taken while` + `dropped while` rebuilds the original.",
		"\tA lambda that isn't a predicate (doesn't return true) counts as not passing, so it ends the run. "
				+ "If the first element already fails, `taken while` is empty and `dropped while` is the whole list.",
		"\tTo filter regardless of position, use Skript's own `%objects% where [{_pred} passes for input]`."
})
@Example("""
		set {_positive} to lambda (n: number): {_n} > 0
		set {_prefix::*} to (5, 3, 1, -2, 4) taken while {_positive} passes    # 5, 3, 1
		set {_rest::*} to (5, 3, 1, -2, 4) dropped while {_positive} passes    # -2, 4
		""")
@Since("1.4.0")
public class ExprTakenWhile extends SimpleExpression<Object> {

	private static final int DROPPED = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprTakenWhile.class, Object.class)
				.supplier(ExprTakenWhile::new)
				.addPatterns(
						"%objects% taken while %object% passes",
						"%objects% dropped while %object% passes")
				.build());
	}

	private Expression<?> source;
	private Expression<?> predicate;
	private boolean dropped;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		predicate = exprs[1];
		dropped = matchedPattern == DROPPED;
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(predicate.getSingle(event));
		Object[] in = source.getArray(event);
		if (lambda == null) return new Object[0];
		int cut = in.length;
		for (int i = 0; i < in.length; i++) {
			if (!Boolean.TRUE.equals(lambda.invoke(new Object[]{in[i]}))) {
				cut = i;
				break;
			}
		}
		return dropped ? Arrays.copyOfRange(in, cut, in.length) : Arrays.copyOfRange(in, 0, cut);
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
		return source.toString(event, debug) + (dropped ? " dropped while " : " taken while ")
				+ predicate.toString(event, debug) + " passes";
	}

}
