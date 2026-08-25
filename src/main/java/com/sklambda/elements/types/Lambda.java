package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.UnparsedLiteral;
import ch.njol.skript.registrations.Classes;
import org.skriptlang.skript.lang.comparator.Comparators;
import org.skriptlang.skript.lang.comparator.Relation;
import ch.njol.skript.variables.Variables;
import com.sklambda.elements.events.LambdaInvocationEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A callable defined in a script.
 *
 * <p>It implements the common {@code java.util.function} shapes directly, so a lambda can be handed
 * to any Java method that wants one. That is what makes reflective calls like
 * {@code {_item}.editMeta({_lambda})} work: the argument really is a {@link Consumer}, so the method
 * lookup matches and the body runs when Java invokes it. Which shape applies is decided by the caller,
 * not the lambda: the same value can be a consumer here and a predicate there.
 */
public final class Lambda implements
		Consumer<Object>, BiConsumer<Object, Object>,
		Function<Object, Object>, Predicate<Object>,
		Supplier<Object>, Runnable, Comparator<Object> {

	public record Param(String name, ClassInfo<?> type, @Nullable Expression<?> defaultValue) {
		public Param(String name, ClassInfo<?> type) {
			this(name, type, null);
		}
	}

	@FunctionalInterface
	public interface Body {
		/** The values the body produced. Empty or null means it returned nothing. */
		Object @Nullable [] run(LambdaInvocationEvent event);
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

	/**
	 * Narrows an arbitrary value to a Lambda, or null if it isn't one.
	 *
	 * <p>skript-reflect's sections and function references are callable in exactly the same way, so they
	 * are adapted here rather than at each of the twenty-odd places a lambda is accepted. That is what
	 * lets `{_list::*} mapped with {_section}` work on a section someone already wrote.
	 */
	public static @Nullable Lambda from(@Nullable Object value) {
		if (value instanceof Lambda lambda) return lambda;
		return ReflectBridge.adapt(value);
	}

	/** A lambda that calls a Skript function, keeping its parameter names and return type. */
	@SuppressWarnings({"deprecation", "removal"})
	public static Lambda fromFunction(ch.njol.skript.lang.function.Function<?> function) {
		List<Param> params = new ArrayList<>();
		for (ch.njol.skript.lang.function.Parameter<?> parameter : function.getParameters()) {
			params.add(new Param(parameter.getName(), parameter.getType()));
		}
		Body body = invocation -> {
			Object[] args = invocation.getArgs();
			Object[][] functionParams = new Object[args.length][];
			for (int i = 0; i < args.length; i++) {
				functionParams[i] = new Object[]{args[i]};
			}
			return function.execute(functionParams);
		};
		return new Lambda(params, function.getReturnType(), body);
	}

	/**
	 * True if {@code expr} is raw text Skript couldn't parse into anything. Such an operand can never hold a
	 * lambda, so syntax expecting one must fail its parse instead of defending the literal: Skript then falls
	 * through to lower-priority patterns, which is how `%objects% mapped with [length of "%input%"]` reaches
	 * Skript's own bracket-body list ops rather than being swallowed here.
	 */
	public static boolean isUnparsed(@Nullable Expression<?> expr) {
		return expr instanceof UnparsedLiteral;
	}

	/**
	 * What a lambda call produced: every value it returned, and whether the body errored out instead of
	 * finishing. A lambda is single-valued by contract, but a body is free to `return` a list, so the
	 * values are carried as an array and narrowed by {@link #value()} for the single-valued callers.
	 */
	public record Outcome(Object @Nullable [] values, boolean errored) {

		/** The first returned value, or null when the body returned nothing. */
		public @Nullable Object value() {
			return values == null || values.length == 0 ? null : values[0];
		}
	}

	/** Calls the lambda and takes its first returned value: what every single-valued caller wants. */
	public @Nullable Object invoke(Object @NotNull [] args) {
		return call(args).value();
	}

	/** Calls the lambda and keeps every value it returned, for callers that accept a list. */
	public Object @NotNull [] invokeAll(Object @NotNull [] args) {
		Object[] values = call(args).values();
		return values == null ? new Object[0] : values;
	}

	/**
	 * Like {@link #invoke} but also reports whether the body errored. Callers that turn a lambda into a
	 * result others observe (futures) need this: a Skript runtime error is swallowed by the trigger and
	 * would otherwise be indistinguishable from a lambda that returned nothing.
	 */
	public Outcome call(Object @NotNull [] args) {
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
				// A required argument is missing. Running the body anyway produces a plausible-looking wrong
				// answer (Skript's arithmetic quietly absorbs the nothing), so refuse the call instead: the
				// caller gets nothing back and, where it matters, a failed future rather than bad data.
				event.markErrored();
				return new Outcome(null, true);
			}
			Variables.setVariable(param.name(), value, event, true);
		}
		Object[] values = body.run(event);
		return new Outcome(values, event.hasErrored());
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
			// The inner call runs on its own event, so carry any error out to this one.
			Outcome outcome = self.call(all);
			if (outcome.errored()) invocation.markErrored();
			return outcome.values();
		};
		return new Lambda(remaining, returnType, body);
	}

	/** A predicate view that passes exactly when this lambda does not (a null/non-boolean result counts as not passing). */
	public Lambda negated() {
		Lambda self = this;
		Body body = invocation -> {
			Outcome outcome = self.call(invocation.getArgs());
			if (outcome.errored()) invocation.markErrored();
			return new Object[]{!Boolean.TRUE.equals(outcome.value())};
		};
		return new Lambda(params, Classes.getExactClassInfo(Boolean.class), body);
	}

	// --- java.util.function shapes -------------------------------------------------------------------
	// One body, several signatures: Java picks whichever the receiving method declares.

	/** {@link Consumer}: runs the body with one argument and drops the result. */
	@Override
	public void accept(@Nullable Object arg) {
		invoke(new Object[]{arg});
	}

	/** {@link BiConsumer}: runs the body with two arguments and drops the result. */
	@Override
	public void accept(@Nullable Object first, @Nullable Object second) {
		invoke(new Object[]{first, second});
	}

	/** {@link Function}: runs the body with one argument and hands back its value. */
	@Override
	public @Nullable Object apply(@Nullable Object arg) {
		return invoke(new Object[]{arg});
	}

	/** {@link Predicate}: anything other than true counts as not passing, as everywhere else in skLambda. */
	@Override
	public boolean test(@Nullable Object arg) {
		return Boolean.TRUE.equals(invoke(new Object[]{arg}));
	}

	/** {@link Supplier}: runs the body with no arguments. */
	@Override
	public @Nullable Object get() {
		return invoke(new Object[0]);
	}

	/** {@link Runnable}: runs the body with no arguments and drops the result. */
	@Override
	public void run() {
		invoke(new Object[0]);
	}

	/** {@link Comparator}: the body is called with both elements and returns their ordering as a number. */
	@Override
	public int compare(@Nullable Object first, @Nullable Object second) {
		Object result = invoke(new Object[]{first, second});
		return result instanceof Number number ? Integer.signum((int) Math.signum(number.doubleValue())) : 0;
	}

	public <T> Predicate<T> asPredicate() {
		return LambdaAdapters.asPredicate(this);
	}

	public <T, R> Function<T, R> asFunction() {
		return LambdaAdapters.asFunction(this);
	}

	public <A, B, R> java.util.function.BiFunction<A, B, R> asBiFunction() {
		return LambdaAdapters.asBiFunction(this);
	}

	public <T> Consumer<T> asConsumer() {
		return LambdaAdapters.asConsumer(this);
	}

	public <R> Supplier<R> asSupplier() {
		return LambdaAdapters.asSupplier(this);
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

		// Two lambdas are the same only when they are the same object; without this Skript has no
		// comparator for the type and `contains` / `is` on a list of them always reads false.
		Comparators.registerComparator(Lambda.class, Lambda.class,
				(first, second) -> Relation.get(first == second));
	}

}
