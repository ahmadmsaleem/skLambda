package com.sklambda.elements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Future;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Future State")
@Description({
		"Checks whether a future is done (resolved), still pending, or failed.",
		"\tSkBee also defines `%advancementprogress% is done` and claims that wording first, so write "
				+ "`future {_f} is done` (or `{_f} is resolved`) when both addons are installed."
})
@Example("""
		if future {_f} is done:
			send "result: %result of {_f}%"
		if {_f} is resolved:
			send "same check, unambiguous wording"
		""")
@Since("1.3.0")
public class CondFutureState extends Condition {

	private static final int DONE = 0;
	private static final int PENDING = 1;
	private static final int FAILED = 2;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondFutureState.class)
				.supplier(CondFutureState::new)
				.addPatterns(
						// Bare forms. `is done` is also SkBee's advancement-progress condition, which is
						// registered first and so wins the line whenever SkBee is installed; the `future ...`
						// spellings below are the unambiguous way to ask, and `resolved` / `completed` never clash.
						"%future% is (done|complete[d]|resolved)",
						"%future% (isn't|is not) (done|complete[d]|resolved)",
						"%future% is pending",
						"%future% (isn't|is not) pending",
						"%future% (has failed|is failed)",
						"%future% (hasn't|has not) failed",
						// Nothing else claims a condition opening with `future`, so these always reach us.
						"[the] future [of] %future% is (done|complete[d]|resolved)",
						"[the] future [of] %future% (isn't|is not) (done|complete[d]|resolved)",
						"[the] future [of] %future% is pending",
						"[the] future [of] %future% (isn't|is not) pending",
						"[the] future [of] %future% (has failed|is failed)",
						"[the] future [of] %future% (hasn't|has not) failed")
				.build());
	}

	private Expression<?> futureExpr;
	private int mode;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		futureExpr = exprs[0];
		if (!couldBeFuture(futureExpr)) return false;
		// The `future ...` spellings repeat the bare six in the same order, so fold them together.
		mode = (matchedPattern % 6) / 2;
		setNegated(matchedPattern % 2 == 1);
		return true;
	}

	/**
	 * Whether {@code expr} might actually yield a future at runtime. A `%future%` slot accepts anything
	 * typed loosely enough to be converted, so an expression with a concrete unrelated type (an advancement
	 * progress, say) reaches here too; declining those lets the next matching syntax have the line.
	 */
	private static boolean couldBeFuture(Expression<?> expr) {
		Class<?> declared = expr.getSource().getReturnType();
		return declared.isAssignableFrom(Future.class) || Future.class.isAssignableFrom(declared);
	}

	@Override
	public boolean check(@NotNull Event event) {
		Future future = Future.from(futureExpr.getSingle(event));
		if (future == null) return isNegated();
		boolean result = switch (mode) {
			case DONE -> future.isDone();
			case PENDING -> !future.isDone();
			case FAILED -> future.isFailed();
			default -> false;
		};
		return result ^ isNegated();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String state = switch (mode) {
			case PENDING -> "pending";
			case FAILED -> "failed";
			default -> "done";
		};
		return futureExpr.toString(event, debug) + (isNegated() ? " is not " : " is ") + state;
	}

}
