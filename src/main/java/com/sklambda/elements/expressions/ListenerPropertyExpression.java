package com.sklambda.elements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.sklambda.elements.sections.SecListen;
import com.sklambda.elements.types.Listener;
import com.sklambda.elements.types.ListenerRegistry;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class ListenerPropertyExpression<T> extends SimpleExpression<T> {

	private @Nullable Expression<Listener> listenerExpr;
	private boolean implicit;

	protected abstract String propertyName();

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		if (matchedPattern == 0) {
			implicit = true;
			if (!SecListen.isInsideListenCallback()) {
				Skript.error(propertyName() + " is only valid inside on trigger / on completion / on timeout, use "
						+ propertyName() + " of %listener% outside.");
				return false;
			}
		} else {
			listenerExpr = (Expression<Listener>) exprs[0];
		}
		return true;
	}

	protected @Nullable Listener resolve(Event event) {
		if (implicit) return ListenerRegistry.currentContext();
		if (listenerExpr == null) return null;
		return Listener.from(listenerExpr.getSingle(event));
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return implicit
				? propertyName()
				: propertyName() + " of " + listenerExpr.toString(event, debug);
	}

}
