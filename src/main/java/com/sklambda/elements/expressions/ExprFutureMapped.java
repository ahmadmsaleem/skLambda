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
import com.sklambda.elements.types.FutureRegistry;
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Chained Future")
@Description({
		"Chains a lambda onto a future: `%future% then %lambda%` returns a NEW future that resolves with the "
				+ "lambda applied to the original's result. The source future is unchanged, so it can still be awaited on its own.",
		"\tSpelled `then` (or `chained with`), NOT `mapped with`: that form belongs to list mapping.",
		"\tWARNING: the lambda runs on a background thread, so its body MUST be thread-safe: pure computation "
				+ "or I/O only, with no Bukkit API (no blocks, entities, inventories, players, or other server "
				+ "state). Touching the server from a background thread will throw or corrupt state.",
		"\tIf the source future fails, the chained future fails with the same reason and the lambda is never "
				+ "called. If the lambda itself throws, the chained future fails with that message.",
		"\tChains as far as you like: each `then` adds a stage that runs once the previous one resolves."
})
@Example("""
		set {_hash} to lambda (text: string) -> string:
			return {_text} hashed with SHA-256
		set {_shorten} to lambda (h: string) -> string:
			return first 8 characters of {_h}

		set {_full} to future of calling lambda {_hash} with "payload"
		set {_short} to {_full} then {_shorten}

		wait for {_short} for at most 10 seconds:
			send "<green>%result of {_short}%" to {_p}
			on failure:
				send "<red>%failure reason of {_short}%" to {_p}
		""")
@Since("1.4.0")
public class ExprFutureMapped extends SimpleExpression<Future> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprFutureMapped.class, Future.class)
				.supplier(ExprFutureMapped::new)
				// NOT `mapped with`: that belongs to ExprMapped (lists) and wins the parse, so a future
				// chained with it would read as a list. `then` is unambiguous and reads better anyway.
				.addPatterns("%object% (then|chained with) %object%")
				.build());
	}

	private Expression<?> futureExpr;
	private Expression<?> lambdaExpr;

	private String origin = "unknown";

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		origin = FutureRegistry.currentOrigin();
		futureExpr = exprs[0];
		lambdaExpr = exprs[1];
		return !Lambda.isUnparsed(lambdaExpr);
	}

	@Override
	protected Future @NotNull [] get(@NotNull Event event) {
		Future source = Future.from(futureExpr.getSingle(event));
		Lambda lambda = Lambda.from(lambdaExpr.getSingle(event));
		if (source == null || lambda == null) return new Future[0];
		return new Future[]{source.mapped(lambda).origin(origin)};
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
		return futureExpr.toString(event, debug) + " then " + lambdaExpr.toString(event, debug);
	}

}
