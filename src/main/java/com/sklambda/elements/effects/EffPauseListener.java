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
import com.sklambda.elements.types.ListenerRegistry;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Pause / Resume Listener")
@Description({
		"Pauses or resumes a listener. While paused, events are ignored and the countdown is frozen.",
		"\tPausing runs the listener's `on pause:` callback; resuming runs `on resume:`.",
		"\t`pause all listeners owned by %object%` pauses every listener scoped to that owner (see the "
				+ "`owner:` entry on `listen`); `resume all listeners owned by %object%` is the inverse.",
		"\tOther addons also use `pause`/`resume`; prefix with `skLambda` (e.g. `skLambda pause {x}`) to "
				+ "force this effect when there's a clash."
})
@Example("""
		pause {shield_listener}
		resume {shield_listener}
		skLambda pause {shield_listener}
		pause all listeners owned by player
		""")
@Since("0.0.2-alpha")
public class EffPauseListener extends Effect {

	private static final int RESUME = 1;
	private static final int PAUSE_OWNED = 2;
	private static final int RESUME_OWNED = 3;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffPauseListener.class)
				.supplier(EffPauseListener::new)
				.addPatterns(
						"[sklambda] pause [listener] %listener%",
						"[sklambda] resume [listener] %listener%",
						"[sklambda] pause all listeners owned by %object%",
						"[sklambda] resume all listeners owned by %object%")
				.build());
	}

	private Expression<?> targetExpr;
	private int mode;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		targetExpr = exprs[0];
		mode = matchedPattern;
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		switch (mode) {
			case PAUSE_OWNED -> ListenerRegistry.pauseAllOwnedBy(targetExpr.getSingle(event));
			case RESUME_OWNED -> ListenerRegistry.resumeAllOwnedBy(targetExpr.getSingle(event));
			default -> {
				Listener listener = Listener.from(targetExpr.getSingle(event));
				if (listener == null) return;
				if (mode == RESUME) listener.resume();
				else listener.pause();
			}
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String target = targetExpr.toString(event, debug);
		return switch (mode) {
			case RESUME -> "resume " + target;
			case PAUSE_OWNED -> "pause all listeners owned by " + target;
			case RESUME_OWNED -> "resume all listeners owned by " + target;
			default -> "pause " + target;
		};
	}

}
