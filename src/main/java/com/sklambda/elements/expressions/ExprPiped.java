package com.sklambda.elements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Piped Value")
@Description({
		"Threads a value left-to-right through a chain of one-argument lambdas, feeding each lambda's result "
				+ "into the next. `pipe x through a, b, c` is `call c with (call b with (call a with x))`.",
		"\tEach lambda is called with the running value as its single argument. A list entry that isn't a "
				+ "lambda is skipped, leaving the value unchanged."
})
@Example("""
		# {_trim}, {_lowercase}, {_capitalize} are single-argument lambdas
		set {_name} to pipe " STEVE " through {_trim}, {_lowercase}, and {_capitalize}
		# "Steve"
		""")
@Since("1.2.0")
public class ExprPiped extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprPiped.class, Object.class)
				.supplier(ExprPiped::new)
				.addPatterns("pipe %object% through %objects%")
				.build());
	}

	private Expression<?> value;
	private Expression<?> lambdas;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		value = LiteralUtils.defendExpression(exprs[0]);
		lambdas = exprs[1];
		return LiteralUtils.canInitSafely(value);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Object current = value.getSingle(event);
		for (Object element : lambdas.getArray(event)) {
			Lambda lambda = Lambda.from(element);
			if (lambda == null) continue;
			current = lambda.invoke(new Object[]{current});
		}
		return current == null ? null : new Object[]{current};
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
		return "pipe " + value.toString(event, debug) + " through " + lambdas.toString(event, debug);
	}

}
