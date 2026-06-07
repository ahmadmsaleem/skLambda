package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Listener Owner")
@Description({
		"The owner a listener was scoped to via its `owner:` entry, or nothing if it has none.",
		"\tForms: `owner of %listener%` and `%listener%'s owner`."
})
@Example("""
		set {shield} to listener for block break:
			owner: player
			on trigger:
				broadcast "broken by %owner of {shield}%"
		register {shield}
		""")
@Since("1.2.0")
public class ExprListenerOwner extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprListenerOwner.class, Object.class)
				.supplier(ExprListenerOwner::new)
				.addPatterns(
						"[the] owner of %listener%",
						"%listener%'[s] owner")
				.build());
	}

	private Expression<Listener> listenerExpr;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		listenerExpr = (Expression<Listener>) exprs[0];
		return true;
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Listener listener = Listener.from(listenerExpr.getSingle(event));
		if (listener == null) return new Object[0];
		Object owner = listener.getOwner();
		return owner == null ? new Object[0] : new Object[]{owner};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "owner of " + listenerExpr.toString(event, debug);
	}

}
