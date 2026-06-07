package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Listener;
import com.sklambda.elements.types.ListenerRegistry;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Active Listeners")
@Description({
		"The listeners currently registered on the server, in creation order, the same set `/sklambda listeners` shows.",
		"\t- `[all] active listeners` is every active listener, across all scripts.",
		"\t- `listeners owned by %object%` is only those scoped to that owner (see the `owner:` entry on `listen`).",
		"\t- `the last created listener` is the single most recently created one that is still active, or nothing."
})
@Example("""
		send "%size of all active listeners% listeners running" to console
		loop listeners owned by player:
			unregister loop-value
		send "newest: %owner of the last created listener%" to console
		""")
@Since("1.1.0")
public class ExprListeners extends SimpleExpression<Listener> {

	private static final int OWNED = 1;
	private static final int LAST = 2;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprListeners.class, Listener.class)
				.supplier(ExprListeners::new)
				.addPatterns(
						"[all] active listeners",
						"[all] listeners owned by %object%",
						"[the] last [created] listener")
				.build());
	}

	private int mode;
	private @Nullable Expression<?> ownerExpr;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		mode = matchedPattern;
		if (mode == OWNED) ownerExpr = exprs[0];
		return true;
	}

	@Override
	protected Listener @Nullable [] get(@NotNull Event event) {
		if (mode == LAST) {
			Listener last = ListenerRegistry.lastCreated();
			return last == null ? new Listener[0] : new Listener[]{last};
		}
		List<Listener> active = ListenerRegistry.activeListeners();
		if (mode == OWNED) {
			Object owner = ownerExpr != null ? ownerExpr.getSingle(event) : null;
			if (owner == null) return new Listener[0];
			List<Listener> owned = new ArrayList<>();
			for (Listener listener : active) {
				if (listener.isOwnedBy(owner)) owned.add(listener);
			}
			return owned.toArray(new Listener[0]);
		}
		return active.toArray(new Listener[0]);
	}

	@Override
	public boolean isSingle() {
		return mode == LAST;
	}

	@Override
	public @NotNull Class<? extends Listener> getReturnType() {
		return Listener.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return switch (mode) {
			case OWNED -> "listeners owned by " + (ownerExpr != null ? ownerExpr.toString(event, debug) : "?");
			case LAST -> "the last created listener";
			default -> "all active listeners";
		};
	}

}
