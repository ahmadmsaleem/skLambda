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
import ch.njol.util.Kleenean;
import com.sklambda.elements.sections.SecListen;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Listener Triggers Remaining")
@Description({
		"How many more times a listener will fire before completion.",
		"",
		"Forms:",
		"\t`remaining triggers` (no operand) only works inside an `on trigger` block.",
		"\t`triggers of %listener%` and `%listener%'s triggers` work anywhere.",
		"",
		"Supports `add`, `set`, and `remove`."
})
@Example("""
		listen for chat:
			triggers: 5
			on trigger:
				send "triggers left: %remaining triggers%" to console
		add 1 to {shield}'s triggers
		set triggers of {shield} to 3
		""")
@Since("0.0.2-alpha")
public class ExprListenerTriggers extends SimpleExpression<Long> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprListenerTriggers.class, Long.class)
				.supplier(ExprListenerTriggers::new)
				.addPatterns(
						"[the] remaining triggers",
						"[the] [remaining] triggers of %listener%",
						"%listener%'[s] [remaining] triggers")
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
				Skript.error("remaining triggers is only valid inside on trigger / on completion / on timeout, use triggers of %listener% outside.");
				return false;
			}
		} else {
			listenerExpr = (Expression<Listener>) exprs[0];
		}
		return true;
	}

	@Override
	protected Long @Nullable [] get(@NotNull Event event) {
		Listener listener = resolve(event);
		if (listener == null) return new Long[0];
		return new Long[]{(long) listener.getRemainingTriggers()};
	}

	private @Nullable Listener resolve(Event event) {
		if (implicit) return Listener.currentContext();
		if (listenerExpr == null) return null;
		return listenerExpr.getSingle(event) instanceof Listener l ? l : null;
	}

	@Override
	public Class<?> @Nullable [] acceptChange(@NotNull ChangeMode mode) {
		return switch (mode) {
			case ADD, REMOVE, SET -> new Class<?>[]{Number.class};
			default -> null;
		};
	}

	@Override
	public void change(@NotNull Event event, Object @Nullable [] delta, @NotNull ChangeMode mode) {
		Listener listener = resolve(event);
		if (listener == null) return;
		int amount = (delta == null || delta.length == 0 || !(delta[0] instanceof Number n)) ? 0 : n.intValue();
		switch (mode) {
			case ADD -> listener.addTriggers(amount);
			case REMOVE -> listener.addTriggers(-amount);
			case SET -> listener.setRemainingTriggers(amount);
			default -> { }
		}
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Long> getReturnType() {
		return Long.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return implicit
				? "remaining triggers"
				: "remaining triggers of " + listenerExpr.toString(event, debug);
	}

}
