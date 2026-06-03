package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.variables.Variables;
import com.sklambda.elements.events.LambdaInvocationEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Lambda {

	public record Param(String name, ClassInfo<?> type) {}

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

	/**
	 * A copy of this lambda that closes over {@code locals} — a {@link Variables#copyLocalVariables}
	 * snapshot of the scope where the lambda was defined. The snapshot is replayed into each
	 * invocation before the parameters are bound, so the body can read the surrounding locals
	 * (e.g. {@code {_reducer}}) while parameters still win on a name clash. Returns {@code this}
	 * unchanged when {@code locals} is null.
	 */
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
		// Replay the captured lexical scope first, then bind parameters so they shadow any
		// captured local of the same name.
		if (capturedLocals != null) Variables.setLocalVariables(event, capturedLocals);
		int count = Math.min(args.length, params.size());
		for (int i = 0; i < count; i++) {
			Variables.setVariable(params.get(i).name(), args[i], event, true);
		}
		return body.run(event);
	}

	// ---- adapters to the java.util.function interfaces ----
	// Bridge a Skript lambda straight into a Java field/API: read the Lambda from a variable, then
	// adapt it, e.g. `Predicate<Player> p = lambda.asPredicate();` — no dynamic proxies needed.

	/** As a {@link Predicate}: passes the single argument and reads the result as a boolean (null/non-boolean ⇒ false). */
	public <T> Predicate<T> asPredicate() {
		return arg -> Boolean.TRUE.equals(invoke(new Object[]{arg}));
	}

	/** As a {@link Function}: passes the single argument and returns the result (caller supplies the expected type). */
	@SuppressWarnings("unchecked")
	public <T, R> Function<T, R> asFunction() {
		return arg -> (R) invoke(new Object[]{arg});
	}

	/** As a {@link BiFunction}: passes both arguments and returns the result. */
	@SuppressWarnings("unchecked")
	public <A, B, R> BiFunction<A, B, R> asBiFunction() {
		return (a, b) -> (R) invoke(new Object[]{a, b});
	}

	/** As a {@link Consumer}: passes the single argument and discards any result. */
	public <T> Consumer<T> asConsumer() {
		return arg -> invoke(new Object[]{arg});
	}

	/** As a {@link Supplier}: invokes with no arguments and returns the result. */
	@SuppressWarnings("unchecked")
	public <R> Supplier<R> asSupplier() {
		return () -> (R) invoke(new Object[0]);
	}

	// ---- combinators ----

	/**
	 * A partially-applied copy: {@code prefix} is pre-bound as the leading arguments, and calling the
	 * result supplies the rest. Binding {@code 5} to a {@code (a, b)} lambda yields a {@code (b)} lambda
	 * that invokes the original with {@code 5} prepended. The declared parameters shrink accordingly, so
	 * arity reads correctly, and the original's captured scope is preserved (it does the actual call).
	 */
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

	/**
	 * A predicate view that inverts this lambda's truthiness: the result passes exactly when this one
	 * does not. Same parameters; the return type is boolean. A null/non-boolean result counts as not
	 * passing, so its negation passes.
	 */
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
