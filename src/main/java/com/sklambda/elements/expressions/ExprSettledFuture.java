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
import com.sklambda.elements.types.Future;
import com.sklambda.elements.types.FutureRegistry;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Settled Future")
@Description({
		"A future that is already finished, for returning a known answer where an async one is expected.",
		"\t`a completed future with %object%` is resolved with that value (or with nothing, if omitted).",
		"\t`a failed future with %string%` has already failed with that reason.",
		"\tThis is what makes a \"sometimes cached, sometimes async\" API uniform: callers always get a "
				+ "future and can always `wait for` it, whether or not any work actually happened."
})
@Example("""
		set {_lookup} to lambda (p: player) -> object:
			if {cache::%uuid of {_p}%} is set:
				return a completed future with {cache::%uuid of {_p}%}
			return future of calling lambda {_slow-lookup} with {_p}
		""")
@Since("1.4.0")
public class ExprSettledFuture extends SimpleExpression<Future> {

	private static final int FAILED = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprSettledFuture.class, Future.class)
				.supplier(ExprSettledFuture::new)
				.addPatterns(
						"[a] (completed|resolved) future [(with|of) %-object%]",
						"[a] failed future [with %-string%]")
				.build());
	}

	private boolean failed;
	private @Nullable Expression<?> valueExpr;

	private String origin = "unknown";

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		origin = FutureRegistry.currentOrigin();
		failed = matchedPattern == FAILED;
		if (exprs[0] != null) {
			valueExpr = LiteralUtils.defendExpression(exprs[0]);
			return LiteralUtils.canInitSafely(valueExpr);
		}
		return true;
	}

	@Override
	protected Future @NotNull [] get(@NotNull Event event) {
		Object value = valueExpr != null ? valueExpr.getSingle(event) : null;
		if (failed) return new Future[]{Future.failed(value == null ? null : String.valueOf(value)).origin(origin)};
		return new Future[]{Future.completed(value).origin(origin)};
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
		return (failed ? "a failed future" : "a completed future")
				+ (valueExpr != null ? " with " + valueExpr.toString(event, debug) : "");
	}

}
