package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Date;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Listener Origin")
@Description({
		"Where a listener came from and how long it has existed, so `/sklambda listeners` output and leak "
				+ "hunting can be done from a script.",
		"\t`script of %listener%` is the script file it was declared in, without the line number.",
		"\t`creation date of %listener%` is when it was created.",
		"\t`age of %listener%` is how long ago that was. Note this counts from CREATION, not registration: "
				+ "a watcher declared with `set ... to a watcher on ...` and registered later already has an age."
})
@Example("""
		loop all active listeners:
			if age of loop-value > 10 minutes:
				send "<yellow>stale listener from %script of loop-value%" to console
				unregister loop-value
		""")
@Since("1.4.0")
public class ExprListenerOrigin extends SimpleExpression<Object> {

	private static final int SCRIPT = 0;
	private static final int CREATED = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprListenerOrigin.class, Object.class)
				.supplier(ExprListenerOrigin::new)
				.addPatterns(
						"[the] script of %listener%",
						"[the] creation date of %listener%",
						"[the] age of %listener%")
				.build());
	}

	private Expression<?> listenerExpr;
	private int mode;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		listenerExpr = exprs[0];
		mode = matchedPattern;
		return true;
	}

	@Override
	protected Object @NotNull [] get(@NotNull Event event) {
		if (!(listenerExpr.getSingle(event) instanceof Listener listener)) return new Object[0];
		return switch (mode) {
			case SCRIPT -> new Object[]{listener.getScriptName()};
			case CREATED -> new Object[]{new Date(listener.getCreatedAtMillis())};
			default -> new Object[]{new Timespan(Timespan.TimePeriod.MILLISECOND, listener.getAgeMillis())};
		};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return switch (mode) {
			case SCRIPT -> String.class;
			case CREATED -> Date.class;
			default -> Timespan.class;
		};
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String what = switch (mode) {
			case SCRIPT -> "script";
			case CREATED -> "creation date";
			default -> "age";
		};
		return what + " of " + listenerExpr.toString(event, debug);
	}

}
