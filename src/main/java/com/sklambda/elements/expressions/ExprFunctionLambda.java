package com.sklambda.elements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.function.Function;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.lang.function.Parameter;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import com.sklambda.elements.types.Lambda.Param;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Function Lambda")
@Description({
		"Wraps an existing Skript function in a lambda, so it can be stored, passed around, and later "
				+ "invoked with `call`/`run`/`passes` like any other lambda.",
		"",
		"The function is looked up by name at runtime. Arguments supplied when the lambda is called are "
				+ "forwarded positionally to the function."
})
@Example("""
		function double(amount: number) :: number:
			return {_amount} * 2

		set {_reward} to function lambda "double"
		set {_result} to call lambda {_reward} with 5    # 10
		""")
@Since("0.0.3-alpha")
public class ExprFunctionLambda extends SimpleExpression<Lambda> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprFunctionLambda.class, Lambda.class)
				.supplier(ExprFunctionLambda::new)
				.addPatterns("function lambda %string%")
				.build());
	}

	private Expression<String> nameExpr;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		nameExpr = (Expression<String>) exprs[0];
		return true;
	}

	@Override
	protected Lambda @Nullable [] get(@NotNull Event event) {
		String name = nameExpr.getSingle(event);
		if (name == null) return new Lambda[0];
		Function<?> function = Functions.getGlobalFunction(name);
		if (function == null) {
			Skript.warning("No global function named '" + name + "' was found for 'function lambda'.");
			return new Lambda[0];
		}
		return new Lambda[]{toLambda(function)};
	}

	@SuppressWarnings({"deprecation", "removal"})
	private static Lambda toLambda(Function<?> function) {
		List<Param> params = new ArrayList<>();
		for (Parameter<?> parameter : function.getParameters()) {
			params.add(new Param(parameter.getName(), parameter.getType()));
		}
		ClassInfo<?> returnType = function.getReturnType();
		Lambda.Body body = invocation -> {
			Object[] args = invocation.getArgs();
			Object[][] functionParams = new Object[args.length][];
			for (int i = 0; i < args.length; i++) {
				functionParams[i] = new Object[]{args[i]};
			}
			Object[] result = function.execute(functionParams);
			return result == null || result.length == 0 ? null : result[0];
		};
		return new Lambda(params, returnType, body);
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Lambda> getReturnType() {
		return Lambda.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "function lambda " + nameExpr.toString(event, debug);
	}

}
