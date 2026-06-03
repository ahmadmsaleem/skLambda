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
import org.skriptlang.skript.lang.comparator.Comparators;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Min / Max By")
@Description({
		"Returns the element of a list with the smallest (`lowest`) or largest (`highest`) key, where a one-argument "
				+ "lambda pulls the key out of each element. Unlike `sorted by`, this hands back the element itself, "
				+ "not the key, and stops at a single result.",
		"\tElements whose key is nothing are ignored. Ties keep the first element seen. An empty list (or one with no "
				+ "comparable keys) returns nothing."
})
@Example("""
		# {_score} = lambda (p: player) -> number: return {_p}'s level
		set {_top} to highest of all players by {_score}
		set {_weakest} to lowest of {_mobs::*} by lambda (e: entity) -> number: health of {_e}
		""")
@Since("1.1.0")
public class ExprMinMaxBy extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprMinMaxBy.class, Object.class)
				.supplier(ExprMinMaxBy::new)
				.addPatterns(
						"[the] (lowest|minimum|min) [element] of %objects% by %object%",
						"[the] (highest|maximum|max) [element] of %objects% by %object%")
				.build());
	}

	private Expression<?> source;
	private Expression<?> keyExtractor;
	private boolean max;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		keyExtractor = exprs[1];
		max = matchedPattern == 1;
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(keyExtractor.getSingle(event));
		if (lambda == null) return null;

		Object best = null;
		Object bestKey = null;
		boolean found = false;
		for (Object element : source.getArray(event)) {
			Object key = lambda.invoke(new Object[]{element});
			if (key == null) continue;
			if (!found || isBetter(key, bestKey)) {
				best = element;
				bestKey = key;
				found = true;
			}
		}
		return found ? new Object[]{best} : null;
	}

	/** Whether {@code key} should replace the current best key, per the chosen direction. */
	private boolean isBetter(Object key, Object bestKey) {
		int relation = Comparators.compare(key, bestKey).getRelation();
		return max ? relation > 0 : relation < 0;
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
		return (max ? "highest of " : "lowest of ") + source.toString(event, debug)
				+ " by " + keyExtractor.toString(event, debug);
	}

}
