package com.sklambda.elements.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import com.sklambda.elements.sections.SecListen;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Skip Trigger")
@Description("Skips the current event inside `on trigger`. The listener stays registered, `remaining triggers` is not decremented, and the rest of the on trigger body is not executed.")
@Example("""
		listen for damage where victim is {_p}:
			on trigger:
				if damage cause is not fall:
					skip trigger
				cancel event
		""")
@Since("0.0.2-alpha")
public class EffSkipTrigger extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffSkipTrigger.class)
				.supplier(EffSkipTrigger::new)
				.addPatterns("skip [this] trigger")
				.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		if (!SecListen.isInsideOnTrigger()) {
			Skript.error("skip trigger can only be used inside an on trigger block.");
			return false;
		}
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Listener.SKIP_FLAG.set(Boolean.TRUE);
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		execute(event);
		return null;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "skip trigger";
	}

}
