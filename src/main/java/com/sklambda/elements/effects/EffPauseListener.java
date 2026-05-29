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

@Name("Pause / Resume Listener")
@Description("Pauses or resumes a listener. While paused, events are ignored and the countdown is frozen.")
@Example("""
		pause {shield_listener}
		resume {shield_listener}
		""")
@Since("0.0.2-alpha")
public class EffPauseListener extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffPauseListener.class)
				.supplier(EffPauseListener::new)
				.addPatterns("pause [listener] %listener%", "resume [listener] %listener%")
				.build());
	}

	private Expression<?> listenerExpr;
	private boolean resume;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		listenerExpr = exprs[0];
		resume = matchedPattern == 1;
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Listener listener = Listener.from(listenerExpr.getSingle(event));
		if (listener == null) return;
		if (resume) listener.resume();
		else listener.pause();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return (resume ? "resume " : "pause ") + listenerExpr.toString(event, debug);
	}

}
