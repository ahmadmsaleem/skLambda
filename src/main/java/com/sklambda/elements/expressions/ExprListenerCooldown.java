package com.sklambda.elements.expressions;

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
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Listener Cooldown")
@Description({
		"A listener's `cooldown:` debounce window, the span ignored after each accepted trigger.",
		"\tForms: `cooldown of %listener%` and `%listener%'s cooldown`.",
		"\tSupports `add`, `set`, and `remove`; a zero (or shorter) value disables the cooldown."
})
@Example("""
		set {shield} to listener for block break:
			cooldown: 3 seconds
			on trigger:
				broadcast "cooldown is %cooldown of {shield}%"
		register {shield}
		set cooldown of {shield} to 5 seconds
		""")
@Since("1.2.0")
public class ExprListenerCooldown extends SimpleExpression<Timespan> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprListenerCooldown.class, Timespan.class)
				.supplier(ExprListenerCooldown::new)
				.addPatterns(
						"[the] cooldown of %listener%",
						"%listener%'[s] cooldown")
				.build());
	}

	private Expression<Listener> listenerExpr;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		listenerExpr = (Expression<Listener>) exprs[0];
		return true;
	}

	@Override
	protected Timespan @Nullable [] get(@NotNull Event event) {
		Listener listener = Listener.from(listenerExpr.getSingle(event));
		if (listener == null) return new Timespan[0];
		return new Timespan[]{new Timespan(TimePeriod.MILLISECOND, listener.getCooldownMillis())};
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
		Listener listener = Listener.from(listenerExpr.getSingle(event));
		if (listener == null) return;
		long ms = (delta == null || delta.length == 0 || !(delta[0] instanceof Timespan ts))
				? 0
				: ts.getAs(TimePeriod.MILLISECOND);
		switch (mode) {
			case ADD -> listener.addCooldownMillis(ms);
			case REMOVE -> listener.addCooldownMillis(-ms);
			case SET -> listener.setCooldownMillis(ms);
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
		return "cooldown of " + listenerExpr.toString(event, debug);
	}

}
