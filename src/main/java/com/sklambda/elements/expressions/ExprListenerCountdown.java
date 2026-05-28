package com.sklambda.elements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.util.Timespan.TimePeriod;
import ch.njol.util.Kleenean;
import com.sklambda.elements.sections.SecListen;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Listener Countdown Remaining")
@Description({
		"How much time is left before a listener's countdown fires `on timeout`.",
		"",
		"Forms:",
		"\t`remaining countdown` (no operand) only works inside an `on trigger` block.",
		"\t`countdown of %listener%` and `%listener%'s countdown` work anywhere.",
		"",
		"Adding or removing time reschedules the pending timeout so `on timeout` fires at the new instant."
})
@Example("""
		listen for damage:
			countdown: 30 seconds
			on trigger:
				send "time left: %remaining countdown%" to victim
		add 10 seconds to {shield}'s countdown
		set countdown of {shield} to 1 minute
		""")
@Since("0.0.2-alpha")
public class ExprListenerCountdown extends SimpleExpression<Timespan> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprListenerCountdown.class, Timespan.class)
				.supplier(ExprListenerCountdown::new)
				.addPatterns(
						"[the] remaining countdown",
						"[the] [remaining] countdown of %listener%",
						"%listener%'[s] [remaining] countdown")
				.build());
	}

	private @Nullable Expression<Listener> listenerExpr;
	private boolean implicit;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		if (matchedPattern == 0) {
			implicit = true;
			if (!SecListen.isInsideListenCallback()) {
				Skript.error("remaining countdown is only valid inside on trigger / on completion / on timeout, use countdown of %listener% outside.");
				return false;
			}
		} else {
			listenerExpr = (Expression<Listener>) exprs[0];
		}
		return true;
	}

	@Override
	protected Timespan @Nullable [] get(@NotNull Event event) {
		Listener listener = resolve(event);
		if (listener == null) return new Timespan[0];
		return new Timespan[]{new Timespan(TimePeriod.MILLISECOND, listener.getRemainingCountdownMillis())};
	}

	private @Nullable Listener resolve(Event event) {
		if (implicit) return Listener.currentContext();
		if (listenerExpr == null) return null;
		return listenerExpr.getSingle(event) instanceof Listener l ? l : null;
	}

	@Override
	public Class<?> @Nullable [] acceptChange(@NotNull ChangeMode mode) {
		return switch (mode) {
			case ADD, REMOVE, SET -> new Class<?>[]{Timespan.class};
			default -> null;
		};
	}

	@Override
	public void change(@NotNull Event event, Object @Nullable [] delta, @NotNull ChangeMode mode) {
		Listener listener = resolve(event);
		if (listener == null) return;
		long ms = (delta == null || delta.length == 0 || !(delta[0] instanceof Timespan ts))
				? 0
				: ts.getAs(TimePeriod.MILLISECOND);
		switch (mode) {
			case ADD -> listener.addCountdownMillis(ms);
			case REMOVE -> listener.addCountdownMillis(-ms);
			case SET -> listener.setCountdownMillis(ms);
			default -> { }
		}
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Timespan> getReturnType() {
		return Timespan.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return implicit
				? "remaining countdown"
				: "remaining countdown of " + listenerExpr.toString(event, debug);
	}

}
