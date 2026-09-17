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
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Hide Commands Where")
@Description({
		"Hides every command a predicate lambda passes for, for all players. The lambda is called with the command label "
				+ "as the player receives it (e.g. `essentials:warp`), then the player.",
		"\tPlayers can still run a hidden command by typing it. Players' lists update right away. Running the same line again "
				+ "replaces its old rule, and reloading the script removes it."
})
@Example("""
		on load:
			set {_namespaced} to lambda (cmd: text): {_cmd} contains ":"
			hide commands where {_namespaced} passes
		""")
@Since("1.6.0")
public class EffHideCommandsWhere extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffHideCommandsWhere.class)
				.supplier(EffHideCommandsWhere::new)
				.addPatterns("hide command[s] where %object% pass[es]")
				.build());
	}

	private Expression<?> predicate;
	private @Nullable Script script;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		predicate = exprs[0];
		if (Lambda.isUnparsed(predicate)) return false;
		script = CommandVisibility.currentScript(getParser());
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Lambda lambda = Lambda.from(predicate.getSingle(event));
		if (lambda != null) CommandVisibility.hideWhere(this, lambda, script);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "hide commands where " + predicate.toString(event, debug) + " passes";
	}

}
