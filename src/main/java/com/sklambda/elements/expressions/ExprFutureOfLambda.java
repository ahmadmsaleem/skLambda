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
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Future of Lambda")
@Description({
		"Starts a lambda off the main thread immediately and returns a future for its result.",
		"\tWARNING: the lambda runs on a background thread, so its body MUST be thread-safe: pure computation or I/O only, "
				+ "with no Bukkit API (no blocks, entities, inventories, players, or other server state). Touching the server "
				+ "from a background thread will throw or corrupt state. Arguments are evaluated on the main thread before the lambda starts.",
		"\tAwait the result with `wait for %future%`, which suspends the trigger without blocking the server.",
		"\tIf the lambda body errors, the future FAILS rather than resolving to nothing: `wait for` routes to "
				+ "`on failure:`, `%future% has failed` is true, and `failure reason of %future%` explains why."
})
@Example("""
		set {_hash} to lambda (text: string) -> string:
			return {_text} hashed with SHA-256
		set {_f} to future of calling lambda {_hash} with "payload"
		wait for {_f} for at most 5 seconds:
			send "hash: %result of {_f}%"
		""")
@Since("1.3.0")
public class ExprFutureOfLambda extends SimpleExpression<Future> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprFutureOfLambda.class, Future.class)
				.supplier(ExprFutureOfLambda::new)
				.addPatterns(
						"[a] future of (calling|running|invoking) lambda %object% [with %-objects%]",
						"[a] future of lambda %object% [with %-objects%]")
				.build());
	}

	private Expression<?> lambdaExpr;
	private @Nullable Expression<?> argsExpr;

	private String origin = "unknown";

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		origin = FutureRegistry.currentOrigin();
		lambdaExpr = exprs[0];
		if (Lambda.isUnparsed(lambdaExpr)) return false;
		if (exprs[1] != null) {
			argsExpr = LiteralUtils.defendExpression(exprs[1]);
			return LiteralUtils.canInitSafely(argsExpr);
		}
		return true;
	}

	@Override
	protected Future @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(lambdaExpr.getSingle(event));
		if (lambda == null) return null;
		// Evaluate args on the main thread, then hand the resolved values to the background invocation.
		Object[] args = argsExpr != null ? argsExpr.getArray(event) : new Object[0];
		return new Future[]{Future.ofLambda(lambda, args).origin(origin)};
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
		return "future of calling lambda " + lambdaExpr.toString(event, debug)
				+ (argsExpr != null ? " with " + argsExpr.toString(event, debug) : "");
	}

}
