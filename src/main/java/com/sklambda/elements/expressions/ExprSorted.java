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
import org.skriptlang.skript.lang.comparator.Relation;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Sorted List")
@Description({
		"Orders a list using a lambda that pulls a sort key out of each element. The lambda is called once per "
				+ "element and returns a comparable value (a number, text, etc.); the list is returned ordered by "
				+ "those keys, ascending.",
		"\tThe sort is stable: elements with equal keys keep their original relative order. Elements whose "
				+ "lambda returns nothing sink to the end, and keys of different kinds are grouped by kind, so "
				+ "one odd element no longer leaves the whole list unsorted."
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

	private record Keyed(Object element, @Nullable Object key, int index) {}

	private Expression<?> source;
	private Expression<?> keyExtractor;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		keyExtractor = exprs[1];
		if (Lambda.isUnparsed(keyExtractor)) return false;
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Object[] in = source.getArray(event);
		if (in.length < 2) return in;
		Lambda lambda = Lambda.from(keyExtractor.getSingle(event));
		if (lambda == null) return in;

		List<Keyed> keyed = new ArrayList<>(in.length);
		for (int i = 0; i < in.length; i++) {
			keyed.add(new Keyed(in[i], lambda.invoke(new Object[]{in[i]}), i));
		}
		keyed.sort(ExprSorted::compareKeys);

		Object[] out = new Object[keyed.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = keyed.get(i).element();
		}
		return out;
	}

	/**
	 * A total order over the keys, which the sort needs to stay well-defined. Skript reports both "equal"
	 * and "no comparator for these two" as a zero relation, so comparing on that alone leaves an entire
	 * list unsorted as soon as one key is nothing or of an odd type. Nothing-keys sink to the end, keys of
	 * different types group by type, and every remaining tie falls back to the original position so the
	 * sort stays stable.
	 */
	private static int compareKeys(Keyed a, Keyed b) {
		if (a.key() == null || b.key() == null) {
			if (a.key() != null) return -1;
			if (b.key() != null) return 1;
			return Integer.compare(a.index(), b.index());
		}
		Relation relation = Comparators.compare(a.key(), b.key());
		if (relation == Relation.SMALLER) return -1;
		if (relation == Relation.GREATER) return 1;
		if (relation == Relation.EQUAL) return Integer.compare(a.index(), b.index());
		// NOT_EQUAL means Skript knows of no ordering for this pair, which includes text against text.
		// Anything naturally comparable to its own kind still has an obvious order, so use it.
		if (a.key().getClass() == b.key().getClass() && a.key() instanceof Comparable<?>) {
			@SuppressWarnings("unchecked")
			int natural = ((Comparable<Object>) a.key()).compareTo(b.key());
			if (natural != 0) return natural;
			return Integer.compare(a.index(), b.index());
		}
		int byType = a.key().getClass().getName().compareTo(b.key().getClass().getName());
		return byType != 0 ? byType : Integer.compare(a.index(), b.index());
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
