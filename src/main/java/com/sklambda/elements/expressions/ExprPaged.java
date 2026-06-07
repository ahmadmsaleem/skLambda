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

import java.util.Arrays;

@Name("Page / Window of a List")
@Description({
		"A single fixed-size slice of a list (1-based), the building block for paging a list into a GUI.",
		"\t`page N of %objects% by S` returns the Nth non-overlapping chunk of size S; the last page may be "
				+ "shorter, and a page past the end is empty. Pair it with `page count of ... by ...`.",
		"\t`window N of %objects% by S` returns the Nth overlapping window of size S, sliding one element at a "
				+ "time (window 1 is items 1 to S, window 2 is items 2 to S+1). Only full windows exist, so a window "
				+ "that would run past the end is empty."
})
@Example("""
		# deal {_items::*} into rows of 9 for a paged GUI
		set {_slots::*} to page {_page} of {_items::*} by 9

		set {_w::*} to window 2 of (1, 2, 3, 4) by 3   # 2, 3, 4
		""")
@Since("1.2.0")
public class ExprPaged extends SimpleExpression<Object> {

	private static final int WINDOW = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprPaged.class, Object.class)
				.supplier(ExprPaged::new)
				.addPatterns(
						"page %number% of %objects% by %number%",
						"window %number% of %objects% by %number%")
				.build());
	}

	private Expression<? extends Number> index;
	private Expression<?> source;
	private Expression<? extends Number> size;
	private boolean window;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		index = (Expression<? extends Number>) exprs[0];
		source = LiteralUtils.defendExpression(exprs[1]);
		size = (Expression<? extends Number>) exprs[2];
		window = matchedPattern == WINDOW;
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		int n = toInt(index.getSingle(event));
		int s = toInt(size.getSingle(event));
		if (n <= 0 || s <= 0) return new Object[0];
		Object[] in = source.getArray(event);
		int start = window ? n - 1 : (n - 1) * s;
		if (start >= in.length) return new Object[0];
		int end;
		if (window) {
			end = start + s;
			if (end > in.length) return new Object[0];
		} else {
			end = Math.min(in.length, start + s);
		}
		return Arrays.copyOfRange(in, start, end);
	}

	private static int toInt(@Nullable Number n) {
		return n == null ? 0 : n.intValue();
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
		return (window ? "window " : "page ") + index.toString(event, debug)
				+ " of " + source.toString(event, debug) + " by " + size.toString(event, debug);
	}

}
