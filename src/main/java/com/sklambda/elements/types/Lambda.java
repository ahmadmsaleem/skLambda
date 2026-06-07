package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.variables.Variables;
import com.sklambda.elements.events.LambdaInvocationEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Lambda {

	public record Param(String name, ClassInfo<?> type, @Nullable Expression<?> defaultValue) {
		public Param(String name, ClassInfo<?> type) {
			this(name, type, null);
		}
	}

	@FunctionalInterface
	public interface Body {
		@Nullable Object run(LambdaInvocationEvent event);
	}

	private final List<Param> params;
	private final @Nullable ClassInfo<?> returnType;
	private final Body body;
	private final @Nullable Object capturedLocals;

	public Lambda(List<Param> params, @Nullable ClassInfo<?> returnType, Body body) {
		this(params, returnType, body, null);
	}

	private Lambda(List<Param> params, @Nullable ClassInfo<?> returnType, Body body, @Nullable Object capturedLocals) {
		this.params = params;
		this.returnType = returnType;
		this.body = body;
		this.capturedLocals = capturedLocals;
	}

	/** A copy closing over {@code locals}, replayed into each invocation before params bind so the body can read them (params still shadow same-named captures). Returns {@code this} if {@code locals} is null. */
	public Lambda capturing(@Nullable Object locals) {
		return locals == null ? this : new Lambda(params, returnType, body, locals);
	}

	/** Narrows an arbitrary value to a Lambda, or null if it isn't one. */
	public static @Nullable Lambda from(@Nullable Object value) {
		return value instanceof Lambda lambda ? lambda : null;
	}

	public @Nullable Object invoke(Object @NotNull [] args) {
		LambdaInvocationEvent event = new LambdaInvocationEvent();
		event.setArgs(args);
		// Replay captured locals first, then bind params so they shadow same-named captures.
		if (capturedLocals != null) Variables.setLocalVariables(event, capturedLocals);
		for (int i = 0; i < params.size(); i++) {
			Param param = params.get(i);
			Object value;
			if (i < args.length) {
				value = args[i];
			} else if (param.defaultValue() != null) {
				value = param.defaultValue().getSingle(event);
			} else {
				continue;
			}
			Variables.setVariable(param.name(), value, event, true);
		}
		return body.run(event);
	}

	/** A partially-applied copy: {@code prefix} is pre-bound as the leading args, the rest supplied at call time, and the declared params shrink to match. */
	public Lambda bind(Object @NotNull [] prefix) {
		if (prefix.length == 0) return this;
		Object[] bound = prefix.clone();
		List<Param> remaining = new ArrayList<>(
				prefix.length < params.size() ? params.subList(prefix.length, params.size()) : List.of());
		Lambda self = this;
		Body body = invocation -> {
			Object[] rest = invocation.getArgs();
			Object[] all = new Object[bound.length + rest.length];
			System.arraycopy(bound, 0, all, 0, bound.length);
			System.arraycopy(rest, 0, all, bound.length, rest.length);
			return self.invoke(all);
		};
		return new Lambda(remaining, returnType, body);
	}

	/** A predicate view that passes exactly when this lambda does not (a null/non-boolean result counts as not passing). */
	public Lambda negated() {
		Lambda self = this;
		Body body = invocation -> !Boolean.TRUE.equals(self.invoke(invocation.getArgs()));
		return new Lambda(params, Classes.getExactClassInfo(Boolean.class), body);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("lambda");
		if (!params.isEmpty()) {
			sb.append(" (");
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) sb.append(", ");
				Param param = params.get(i);
				sb.append(param.name()).append(": ").append(param.type().getCodeName());
				if (param.defaultValue() != null) sb.append(" = ").append(param.defaultValue().toString(null, false));
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
