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
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Hide or Show Commands for Players")
@Description({
		"Hides commands from specific players, or shows them again. Players can still run a hidden command by typing it.",
		"\tA hide lasts until the matching `show` runs (logging out does not clear it), the script that hid it is reloaded, or the server restarts.",
		"\t`show` only undoes `hide ... from`: it can't bring back a command a `show only` or `hide commands where` rule removes, "
				+ "or one the player has no permission for. Names match the exact label, ignoring case and a leading `/`."
})
@Example("""
		on damage of player:
			hide commands "spawn" and "home" from victim
			wait 10 seconds
			show commands "spawn" and "home" to victim
		""")
@Since("1.6.0")
public class EffPlayerCommands extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffPlayerCommands.class)
				.supplier(EffPlayerCommands::new)
				.addPatterns(
						"hide command[s] %strings% from %players%",
						"show command[s] %strings% to %players%")
				.build());
	}

	private Expression<?> names;
	private Expression<? extends Player> players;
	private boolean hide;
	private @Nullable Script script;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		names = exprs[0];
		players = (Expression<? extends Player>) exprs[1];
		hide = matchedPattern == 0;
		script = CommandVisibility.currentScript(getParser());
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Player[] targets = players.getArray(event);
		if (targets.length == 0) return;
		if (hide) {
			CommandVisibility.hide(targets, CommandVisibility.names(names.getArray(event)), script);
		} else {
			CommandVisibility.show(targets, CommandVisibility.names(names.getArray(event)));
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return (hide ? "hide commands " : "show commands ") + names.toString(event, debug)
				+ (hide ? " from " : " to ") + players.toString(event, debug);
	}

}
