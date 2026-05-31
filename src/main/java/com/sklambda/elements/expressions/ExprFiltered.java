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

import java.util.ArrayList;
import java.util.List;

@Name("Filtered List")
@Description({
		"Keeps only the elements of a list for which a predicate passes, returning a shorter list.",
		"\tThe predicate is invoked once per element with that element as its argument. With several predicates "
				+ "(a list), an element is kept only if all of them pass — the same all-of semantics as the bare "
				+ "`passes` condition."
})
@Example("""
		# {_is-op} = lambda (p: player): {_p} is op
		set {_mods::*} to {_players::*} filtered where {_is-op} passes
		""")
@Since("1.0.0")
public class ExprFiltered extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprFiltered.class, Object.class)
				.supplier(ExprFiltered::new)
				.addPatterns("%objects% filtered where %objects% pass[es]")
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
		Object[] in = source.getArray(event);
		List<Object> out = new ArrayList<>();
		for (Object element : in) {
			if (LambdaListOps.allPass(preds, element)) out.add(element);
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
		return source.toString(event, debug) + " filtered where " + predicates.toString(event, debug) + " passes";
	}

}
