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

@Name("First Where")
@Description({
		"Returns the first element of a list for which a predicate passes, and stops looking — cheaper than "
				+ "filtering the whole list and taking element 1.",
		"\tWith several predicates (a list), the first element for which all of them pass is returned. Returns "
				+ "nothing if no element matches."
})
@Example("""
		# {_alive} = lambda (p: player): health of {_p} > 0
		set {_winner} to first of {_players::*} where {_alive} passes
		""")
@Since("1.0.0")
public class ExprFirstWhere extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprFirstWhere.class, Object.class)
				.supplier(ExprFirstWhere::new)
				.addPatterns("first [element] of %objects% where %objects% pass[es]")
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
	protected Object @Nullable [] get(@NotNull Event event) {
		Object[] preds = predicates.getArray(event);
		for (Object element : source.getArray(event)) {
			if (LambdaListOps.allPass(preds, element)) return new Object[]{element};
		}
		return null;
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
		return "first of " + source.toString(event, debug) + " where " + predicates.toString(event, debug) + " passes";
	}

}
