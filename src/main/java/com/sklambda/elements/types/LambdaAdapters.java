package com.sklambda.elements.types;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Bridges a {@link Lambda} into the java.util.function interfaces, so a lambda read from a Skript
 * variable can be handed to a Java field or API directly, without a dynamic proxy.
 */
public final class LambdaAdapters {

	private LambdaAdapters() {}

	/** Reads the single-argument result as a boolean; null or non-boolean counts as false. */
	public static <T> Predicate<T> asPredicate(Lambda lambda) {
		return arg -> Boolean.TRUE.equals(lambda.invoke(new Object[]{arg}));
	}

	/** Passes one argument; the caller supplies the expected return type. */
	@SuppressWarnings("unchecked")
	public static <T, R> Function<T, R> asFunction(Lambda lambda) {
		return arg -> (R) lambda.invoke(new Object[]{arg});
	}

	/** Passes both arguments and returns the result. */
	@SuppressWarnings("unchecked")
	public static <A, B, R> BiFunction<A, B, R> asBiFunction(Lambda lambda) {
		return (a, b) -> (R) lambda.invoke(new Object[]{a, b});
	}

	/** Passes one argument and discards any result. */
	public static <T> Consumer<T> asConsumer(Lambda lambda) {
		return arg -> lambda.invoke(new Object[]{arg});
	}

	/** Invokes with no arguments and returns the result. */
	@SuppressWarnings("unchecked")
	public static <R> Supplier<R> asSupplier(Lambda lambda) {
		return () -> (R) lambda.invoke(new Object[0]);
	}
}
