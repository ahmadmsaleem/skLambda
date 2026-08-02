package com.sklambda.elements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Future;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Fail Future")
@Description({
		"Fails one or more futures, waking any trigger waiting on them into its `on failure:` branch. "
				+ "A future that is already resolved or already failed is left unchanged (same rule as `complete`).",
		"\tWithout a `with` clause the reason defaults to \"" + Future.DEFAULT_FAILURE_REASON + "\", so "
				+ "`failure reason of` always reads for a failed future.",
		"\tRead the reason back with `failure reason of %future%`, and test for it with `%future% has failed`."
})
@Example("""
		set {_f} to a new future

		listen for quit where event-player is {_p}:
			owner: {_p}
			triggers: 1
			on trigger:
				fail {_f} with "player disconnected"

		wait for {_f} for at most 30 seconds:
			send "<green>Got: %result of {_f}%" to {_p}
			on failure:
				# to console, not to {_p}: the failure above fires because they left.
				send "<red>Failed: %failure reason of {_f}%" to console
			on timeout:
				send "<yellow>Timed out." to {_p}
		""")
@Since("1.4.0")
public class EffFailFuture extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffFailFuture.class)
				.supplier(EffFailFuture::new)
				// No `break` alias: Skript core owns `break %blocks% [naturally]`, and a variable
				// satisfies both slots, so `break {_f}` would silently bind to that instead.
				.addPatterns(
						"fail %futures% [with %-string%]",
						"reject %futures% [with %-string%]")
				.build());
	}

	private Expression<?> futuresExpr;
	private @Nullable Expression<String> reasonExpr;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		futuresExpr = exprs[0];
		reasonExpr = (Expression<String>) exprs[1];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		String reason = reasonExpr != null ? reasonExpr.getSingle(event) : null;
		for (Object candidate : futuresExpr.getArray(event)) {
			Future future = Future.from(candidate);
			if (future != null) future.fail(reason);
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "fail " + futuresExpr.toString(event, debug)
				+ (reasonExpr != null ? " with " + reasonExpr.toString(event, debug) : "");
	}

}
