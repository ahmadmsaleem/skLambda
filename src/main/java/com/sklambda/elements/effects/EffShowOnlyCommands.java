package com.sklambda.elements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.CommandVisibility;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Show Only Commands")
@Description({
		"Limits the commands players see when they press `/` to the ones listed. Players can still run a hidden command by typing it.",
		"\tThe `where` condition is checked for each player whenever their command list is sent, with `player` being that player, "
				+ "so it can check permissions even from `on load`. Local variables from where the line ran can be used in it.",
		"\tSeveral rules add up: a player sees every command from each rule whose condition passes for them. Once any rule exists, "
				+ "a player who matches none of them sees no commands.",
		"\tNames match the exact label, ignoring case and a leading `/`, so aliases and `plugin:command` labels need listing separately. "
				+ "Players' lists update right away. Running the same line again replaces its old rule, and reloading the script removes it."
})
@Example("""
		on load:
			show only commands "help", "msg", "r" and "tpa" where player has permission "group.default"
			show only commands "ban", "kick" and "mute" where player has permission "group.mod"
			show only commands {admin-commands::*} where player has permission "group.admin"
		""")
@Since("1.6.0")
public class EffShowOnlyCommands extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffShowOnlyCommands.class)
				.supplier(EffShowOnlyCommands::new)
				.addPatterns("show only command[s] %strings% [where <.+>]")
				.build());
	}

	private Expression<?> names;
	private @Nullable Condition condition;
	private @Nullable Script script;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		names = exprs[0];
		ParserInstance parser = getParser();
		script = CommandVisibility.currentScript(parser);
		if (parseResult.regexes.isEmpty()) return true;

		String text = parseResult.regexes.get(0).group().trim();
		Class<? extends Event>[] outerEvents = parser.getCurrentEvents();
		String outerName = parser.getCurrentEventName();
		parser.setCurrentEvent("send command list", PlayerCommandSendEvent.class);
		try {
			condition = Condition.parse(text, "Can't understand this condition: " + text);
		} finally {
			if (outerEvents != null) {
				parser.setCurrentEvent(outerName, outerEvents);
			} else {
				parser.deleteCurrentEvent();
			}
		}
		return condition != null;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Object locals = condition == null ? null : Variables.copyLocalVariables(event);
		CommandVisibility.showOnly(this, CommandVisibility.names(names.getArray(event)), condition, locals, script);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "show only commands " + names.toString(event, debug)
				+ (condition != null ? " where " + condition.toString(event, debug) : "");
	}

}
