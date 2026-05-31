package com.sklambda.elements.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.sklambda.elements.sections.SecListen;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Cancel / Unregister Listener (in trigger)")
@Description("Silently stops the surrounding `listen`. Does not fire `on completion` or `on timeout`, but `on end` still runs (with `end reason` = `cancelled`). Only valid inside `on trigger`. `unregister listener` and `cancel listener` are aliases.")
@Example("""
		listen for block break:
			on trigger:
				if event-block is stone:
					unregister listener
		""")
@Since("0.0.1-alpha")
public class EffCancelListener extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffCancelListener.class)
				.supplier(EffCancelListener::new)
				.addPatterns("unregister listener", "cancel listener")
				.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		if (!SecListen.isInsideOnTrigger()) {
			Skript.error("unregister listener can only be used inside an on trigger block.");
			return false;
		}
		SecListen.markSawComplete();
		return true;
	}

	@Override
	protected void execute(@NotNull Event event) {
		Listener listener = Listener.currentContext();
		if (listener != null) listener.markCancel();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "unregister listener";
	}

}
