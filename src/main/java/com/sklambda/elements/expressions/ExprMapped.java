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

import java.util.ArrayList;
import java.util.List;

@Name("Mapped List")
@Description({
		"Transforms every element of a list with a one-argument value lambda, returning a new list the same size.",
		"\tThe lambda is invoked once per element, with the element as its single argument; its return value "
				+ "takes that element's place. Elements whose lambda returns nothing are dropped."
})
@Example("""
		# {_double} = lambda (n: number) -> number: return {_n} * 2
		set {_doubled::*} to {_nums::*} mapped with {_double}
		""")
@Since("1.0.0")
public class ExprMapped extends SimpleExpression<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprMapped.class, Object.class)
				.supplier(ExprMapped::new)
				.addPatterns("%objects% mapped (with|using|through) %object%")
				.build());
	}

	private Expression<?> source;
	private Expression<?> mapper;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		source = LiteralUtils.defendExpression(exprs[0]);
		mapper = exprs[1];
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected Object @Nullable [] get(@NotNull Event event) {
		Lambda lambda = Lambda.from(mapper.getSingle(event));
		if (lambda == null) return new Object[0];
		Object[] in = source.getArray(event);
		List<Object> out = new ArrayList<>(in.length);
		for (Object element : in) {
			Object result = lambda.invoke(new Object[]{element});
			if (result != null) out.add(result);
		}
		return out.toArray();
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public @NotNull Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return source.toString(event, debug) + " mapped with " + mapper.toString(event, debug);
	}

}
