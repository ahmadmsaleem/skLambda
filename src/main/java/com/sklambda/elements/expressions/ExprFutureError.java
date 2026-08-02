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

import java.util.ArrayList;
import java.util.List;

@Name("Future Failure Reason")
@Description({
		"Why one or more futures failed. Returns nothing for futures that are still pending or that "
				+ "resolved successfully, so it doubles as a failure test.",
		"\tFor a future failed with `fail ... with \"reason\"`, this is that reason. For one failed by a "
				+ "background lambda throwing, it is the exception's message (or its type, when it has none). "
				+ "It is never nothing for a failed future."
})
@Example("""
		if {_f} has failed:
			send "<red>%failure reason of {_f}%" to console
		""")
@Since("1.4.0")
public class ExprFutureError extends SimpleExpression<String> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprFutureError.class, String.class)
				.supplier(ExprFutureError::new)
				.addPatterns("[the] (error|failure reason|failure|exception) of %futures%")
				.build());
	}

	private Expression<?> futuresExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		futuresExpr = exprs[0];
		return true;
	}

	@Override
	protected String @NotNull [] get(@NotNull Event event) {
		List<String> reasons = new ArrayList<>();
		for (Object candidate : futuresExpr.getArray(event)) {
			Future future = Future.from(candidate);
			if (future == null) continue;
			String reason = future.failureReason();
			if (reason != null) reasons.add(reason);
		}
		return reasons.toArray(new String[0]);
	}

	@Override
	public boolean isSingle() {
		return futuresExpr.isSingle();
	}

	@Override
	public @NotNull Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "failure reason of " + futuresExpr.toString(event, debug);
	}

}
