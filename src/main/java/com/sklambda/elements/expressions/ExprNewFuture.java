package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Future;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("New Future")
@Description("Creates an unresolved future (a promise). Complete it later with `complete %future% with ...`, and await it with `wait for`.")
@Example("""
		set {_f} to a new future
		complete {_f} with "ready"
		""")
@Since("1.3.0")
public class ExprNewFuture extends SimpleExpression<Future> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprNewFuture.class, Future.class)
				.supplier(ExprNewFuture::new)
				.addPatterns("a [new] [empty|pending] future")
				.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		return true;
	}

	@Override
	protected Future @NotNull [] get(@NotNull Event event) {
		return new Future[]{Future.pending()};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Future> getReturnType() {
		return Future.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "a new future";
	}

}
