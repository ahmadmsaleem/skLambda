package com.sklambda.elements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Listener State")
@Description("Checks whether a listener is registered, paused, or resumed (running).")
@Example("""
		if {shield_listener} is paused:
			resume {shield_listener}
		""")
@Since("0.0.2-alpha")
public class CondListenerState extends Condition {

	private static final int REGISTERED = 0;
	private static final int PAUSED = 1;
	private static final int RESUMED = 2;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondListenerState.class)
				.supplier(CondListenerState::new)
				.addPatterns(
						"%object% is registered [listener]",
						"%object% (isn't|is not) registered [listener]",
						"%object% is paused [listener]",
						"%object% (isn't|is not) paused [listener]",
						"%object% is (resumed|running|active) [listener]",
						"%object% (isn't|is not) (resumed|running|active) [listener]")
				.build());
	}

	private Expression<?> listenerExpr;
	private int mode;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		listenerExpr = exprs[0];
		mode = matchedPattern / 2;
		setNegated(matchedPattern % 2 == 1);
		return true;
	}

	@Override
	public boolean check(@NotNull Event event) {
		Object value = listenerExpr.getSingle(event);
		if (!(value instanceof Listener listener)) return isNegated();
		boolean result = switch (mode) {
			case REGISTERED -> listener.isActive();
			case PAUSED -> listener.isPaused();
			case RESUMED -> listener.isActive() && !listener.isPaused();
			default -> false;
		};
		return result ^ isNegated();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String state = switch (mode) {
			case PAUSED -> "paused";
			case RESUMED -> "resumed";
			default -> "registered";
		};
		return listenerExpr.toString(event, debug) + (isNegated() ? " is not " : " is ") + state;
	}

}
