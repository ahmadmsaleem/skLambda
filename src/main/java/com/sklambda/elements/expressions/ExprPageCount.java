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

@Name("Page / Window Count")
@Description({
		"How many pages or windows of a given size a list splits into, for sizing GUI navigation.",
		"\t`page count of %objects% by S` is the number of size-S chunks (rounded up, so a partial last page "
				+ "still counts).",
		"\t`window count of %objects% by S` is the number of size-S sliding windows: `length - S + 1`, or 0 if "
				+ "the list is shorter than S."
})
@Example("""
		set {_pages} to page count of {_items::*} by 9
		loop {_pages} times:
			set {_slots::*} to page loop-value of {_items::*} by 9
		""")
@Since("1.2.0")
public class ExprPageCount extends SimpleExpression<Long> {

	private static final int WINDOW = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprPageCount.class, Long.class)
				.supplier(ExprPageCount::new)
				.addPatterns(
						"[the] (page count|number of pages) of %objects% by %number%",
						"[the] (window count|number of windows) of %objects% by %number%")
				.build());
	}

	private Expression<?> source;
	private Expression<? extends Number> size;
	private boolean window;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		size = (Expression<? extends Number>) exprs[1];
		window = matchedPattern == WINDOW;
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Long @Nullable [] get(@NotNull Event event) {
		Number raw = size.getSingle(event);
		int s = raw == null ? 0 : raw.intValue();
		if (s <= 0) return new Long[]{0L};
		int n = source.getArray(event).length;
		long count = window ? Math.max(0, n - s + 1) : (n + s - 1) / s;
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
		return (window ? "window count of " : "page count of ")
				+ source.toString(event, debug) + " by " + size.toString(event, debug);
	}

}
