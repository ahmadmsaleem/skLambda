package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.variables.Variables;
import com.sklambda.elements.events.LambdaInvocationEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Lambda {

	public record Param(String name, ClassInfo<?> type) {}

	@FunctionalInterface
	public interface Body {
		@Nullable Object run(LambdaInvocationEvent event);
	}

	private final List<Param> params;
	private final @Nullable ClassInfo<?> returnType;
	private final Body body;

	public Lambda(List<Param> params, @Nullable ClassInfo<?> returnType, Body body) {
		this.params = params;
		this.returnType = returnType;
		this.body = body;
	}

	/** Narrows an arbitrary value to a Lambda, or null if it isn't one. */
	public static @Nullable Lambda from(@Nullable Object value) {
		return value instanceof Lambda lambda ? lambda : null;
	}

	public @Nullable Object invoke(Object @NotNull [] args) {
		LambdaInvocationEvent event = new LambdaInvocationEvent();
		event.setArgs(args);
		int count = Math.min(args.length, params.size());
		for (int i = 0; i < count; i++) {
			Variables.setVariable(params.get(i).name(), args[i], event, true);
		}
		return body.run(event);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("lambda");
		if (!params.isEmpty()) {
			sb.append(" (");
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) sb.append(", ");
				sb.append(params.get(i).name()).append(": ").append(params.get(i).type().getCodeName());
			}
			sb.append(")");
		}
		if (returnType != null) {
			sb.append(" -> ").append(returnType.getCodeName());
		}
		return sb.toString();
	}

	public static void register() {
		Classes.registerClass(new ClassInfo<>(Lambda.class, "lambda")
				.user("lambdas?")
				.name("Lambda")
				.description("A callable lambda with optional typed parameters and return type.")
				.since("0.0.1-alpha")
				.parser(new Parser<>() {
					@Override
					public boolean canParse(@NotNull ParseContext context) {
						return false;
					}

					@Override
					public @NotNull String toString(Lambda lambda, int flags) {
						return lambda.toString();
					}

					@Override
					public @NotNull String toVariableNameString(Lambda lambda) {
						return lambda.toString();
					}
				}));
	}

}
