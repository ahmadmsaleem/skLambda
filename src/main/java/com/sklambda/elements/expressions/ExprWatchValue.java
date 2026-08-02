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
import com.sklambda.elements.types.ListenerRegistry;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Watched Value")
@Description("Inside a watcher's `on change` block, the value before the change (`old value`) and after it (`new value`).")
@Example("""
		watch (level of {_p}) every 2 seconds:
			on change:
				send "level: %old value% -> %new value%" to {_p}
		""")
@Since("1.3.0")
public class ExprWatchValue extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprWatchValue.class, Object.class)
				.supplier(ExprWatchValue::new)
				.addPatterns(
						"[the] (old|previous|former) value",
						"[the] (new|current|changed) value")
				.build());
	}

	private boolean isNew;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		isNew = matchedPattern == 1;
		return true;
	}

	@Override
	protected Object @NotNull [] get(@NotNull Event event) {
		Listener listener = ListenerRegistry.currentContext();
		if (listener == null) return new Object[0];
		Object value = isNew ? listener.getWatchNew() : listener.getWatchOld();
		return value == null ? new Object[0] : new Object[]{value};
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
		return isNew ? "new value" : "old value";
	}

}
