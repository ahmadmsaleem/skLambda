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
@Description("Checks whether a future is done (resolved), still pending, or failed.")
@Example("""
		if {_f} is done:
			send "result: %result of {_f}%"
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
						"%future% is (done|complete[d]|resolved)",
						"%future% (isn't|is not) (done|complete[d]|resolved)",
						"%future% is pending",
						"%future% (isn't|is not) pending",
						"%future% (has failed|is failed)",
						"%future% (hasn't|has not) failed")
				.build());
	}

	private Expression<?> futureExpr;
	private int mode;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		futureExpr = exprs[0];
		mode = matchedPattern / 2;
		setNegated(matchedPattern % 2 == 1);
		return true;
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
