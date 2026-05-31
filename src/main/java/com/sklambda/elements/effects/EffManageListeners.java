package com.sklambda.elements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Unregister All / Last / Owned Listeners")
@Description({
		"Bulk listener cleanup:",
		"\t`unregister all listeners` stops every active listener on the server (across all scripts).",
		"\t`unregister the last created listener` stops the most recently created one that is still active.",
		"\t`unregister all listeners owned by %object%` stops only the listeners scoped to that owner (see the `owner:` entry on `listen`).",
		"",
		"None of these fire `on completion` or `on timeout`, but each stopped listener's `on end` callback still runs (with `end reason` = `unregistered`)."
})
@Example("""
		unregister all listeners
		unregister the last created listener
		unregister all listeners owned by player
		""")
@Since("0.0.3-alpha")
public class EffManageListeners extends Effect {

	private static final int ALL = 0;
	private static final int LAST = 1;
	private static final int OWNED = 2;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffManageListeners.class)
				.supplier(EffManageListeners::new)
				.addPatterns(
						"unregister all listeners",
						"unregister [the] last [created] listener",
						"unregister all listeners owned by %object%")
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
	protected void execute(@NotNull Event event) {
		switch (mode) {
			case ALL -> Listener.unregisterAll();
			case OWNED -> {
				if (ownerExpr != null) Listener.unregisterAllOwnedBy(ownerExpr.getSingle(event));
			}
			case LAST -> {
				Listener last = Listener.lastCreated();
				if (last != null) last.unregister();
			}
			default -> { }
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return switch (mode) {
			case OWNED -> "unregister all listeners owned by " + (ownerExpr != null ? ownerExpr.toString(event, debug) : "?");
			case LAST -> "unregister the last created listener";
			default -> "unregister all listeners";
		};
	}

}
