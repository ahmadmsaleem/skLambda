package com.sklambda.elements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.CommandVisibility;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Reset Command Visibility")
@Description({
		"Puts command lists back to normal.",
		"\tWithout players, removes every `show only commands` and `hide commands where` rule from all scripts, and every player's hides.",
		"\tWith players, only removes the hides those players got from `hide commands ... from`; the rules still apply."
})
@Example("""
		reset command visibility
		reset command visibility for victim
		""")
@Since("1.6.0")
public class EffResetCommandVisibility extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffResetCommandVisibility.class)
				.supplier(EffResetCommandVisibility::new)
				.addPatterns("reset [the] command visibility [(for|of) %-players%]")
				.build());
	}

	private @Nullable Expression<? extends Player> players;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		players = (Expression<? extends Player>) exprs[0];
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		if (players == null) {
			CommandVisibility.reset();
		} else {
			CommandVisibility.reset(players.getArray(event));
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "reset command visibility" + (players != null ? " for " + players.toString(event, debug) : "");
	}

}
