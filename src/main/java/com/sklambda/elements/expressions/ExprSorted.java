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

import java.util.ArrayList;
import java.util.List;

@Name("Sorted List")
@Description({
		"Orders a list using a lambda that pulls a sort key out of each element. The lambda is called once per "
				+ "element and returns a comparable value (a number, text, etc.); the list is returned ordered by "
				+ "those keys, ascending.",
		"\tElements whose keys can't be compared keep their original relative order (the sort is stable)."
})
@Example("""
		# {_score} = lambda (p: player) -> number: return {_p}'s level
		set {_ranked::*} to {_players::*} sorted by {_score}
		""")
@Since("1.0.0")
public class ExprSorted extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprSorted.class, Object.class)
				.supplier(ExprSorted::new)
				.addPatterns("%objects% sorted by %object%")
				.build());
	}

	private record Keyed(Object element, @Nullable Object key) {}

	private Expression<?> source;
	private Expression<?> keyExtractor;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		keyExtractor = exprs[1];
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Object[] in = source.getArray(event);
		if (in.length < 2) return in;
		Lambda lambda = Lambda.from(keyExtractor.getSingle(event));
		if (lambda == null) return in;

		List<Keyed> keyed = new ArrayList<>(in.length);
		for (Object element : in) {
			keyed.add(new Keyed(element, lambda.invoke(new Object[]{element})));
		}
		keyed.sort((a, b) -> Comparators.compare(a.key(), b.key()).getRelation());

		Object[] out = new Object[keyed.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = keyed.get(i).element();
		}
		return out;
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
		return source.toString(event, debug) + " sorted by " + keyExtractor.toString(event, debug);
	}

}
