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

@Name("Unregister All / Last Listener")
@Description({
		"Bulk listener cleanup:",
		"\t`unregister all listeners` stops every active listener on the server (across all scripts).",
		"\t`unregister the last created listener` stops the most recently created one that is still active.",
		"",
		"Neither fires `on completion` or `on timeout`."
})
@Example("""
		unregister all listeners
		unregister the last created listener
		""")
@Since("0.0.3-alpha")
public class EffManageListeners extends Effect {

	private static final int ALL = 0;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffManageListeners.class)
				.supplier(EffManageListeners::new)
				.addPatterns(
						"unregister all listeners",
						"unregister [the] last [created] listener")
				.build());
	}

	private int mode;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		mode = matchedPattern;
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		if (mode == ALL) {
			Listener.unregisterAll();
		} else {
			Listener last = Listener.lastCreated();
			if (last != null) last.unregister();
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return mode == ALL ? "unregister all listeners" : "unregister the last created listener";
	}

}
