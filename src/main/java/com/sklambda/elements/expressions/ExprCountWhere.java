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
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Count Where")
@Description({
		"Counts how many elements of a list a predicate passes for — like a filter, but it returns just the number "
				+ "of matches instead of the matching elements.",
		"\tWith several predicates (a list), an element counts only if all of them pass."
})
@Example("""
		# {_is-op} = lambda (p: player): {_p} is op
		set {_n} to count of {_players::*} where {_is-op} passes
		""")
@Since("1.0.0")
public class ExprCountWhere extends SimpleExpression<Long> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprCountWhere.class, Long.class)
				.supplier(ExprCountWhere::new)
				.addPatterns("count of %objects% where %objects% pass[es]")
				.build());
	}

	private Expression<?> source;
	private Expression<?> predicates;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		predicates = LiteralUtils.defendExpression(exprs[1]);
		return LiteralUtils.canInitSafely(source) && LiteralUtils.canInitSafely(predicates);
	}

	@Override
	protected Long @Nullable [] get(@NotNull Event event) {
		Object[] preds = predicates.getArray(event);
		Object[] in = source.getArray(event);
		long count = 0;
		for (Object element : in) {
			if (LambdaListOps.allPass(preds, element)) count++;
		}
		return new Long[]{count};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Long> getReturnType() {
		return Long.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "count of " + source.toString(event, debug) + " where " + predicates.toString(event, debug) + " passes";
	}

}
