package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import com.sklambda.elements.types.EndReason;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Listener End Reason")
@Description({
		"Why a listener stopped: `completion`, `timeout`, `cancelled`, or `unregistered`.",
		"\t",
		"Forms:",
		"\t- `end reason` (no operand) only works inside an `on end` block.",
		"\t- `end reason of %listener%` and `%listener%'s end reason` work anywhere (null until it ends)."
})
@Example("""
		listen for chat:
			countdown: 10 seconds
			on end:
				if end reason is timeout:
					send "listener timed out" to console
		""")
@Since("1.0.0")
public class ExprEndReason extends ListenerPropertyExpression<EndReason> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprEndReason.class, EndReason.class)
				.supplier(ExprEndReason::new)
				.addPatterns(
						"[the] end reason",
						"[the] end reason of %listener%",
						"%listener%'[s] end reason")
				.build());
	}

	@Override
	protected String propertyName() {
		return "end reason";
	}

	@Override
	protected EndReason @Nullable [] get(@NotNull Event event) {
		Listener listener = resolve(event);
		if (listener == null) return new EndReason[0];
		EndReason reason = listener.getEndReason();
		return reason == null ? new EndReason[0] : new EndReason[]{reason};
	}

	@Override
	public @NotNull Class<? extends EndReason> getReturnType() {
		return EndReason.class;
	}

}
