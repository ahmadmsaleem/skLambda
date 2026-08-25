package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import org.skriptlang.skript.lang.comparator.Comparators;
import org.skriptlang.skript.lang.comparator.Relation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A first-class value wrapping a {@link CompletableFuture}: an eventual result you can store, pass,
 * complete, combine, and await. Backed by a shared daemon thread pool for off-thread lambda calls.
 */
public final class Future {

	private static final AtomicLong THREAD_COUNTER = new AtomicLong();
	private static volatile @Nullable ExecutorService pool;

	private final CompletableFuture<Object> delegate;
	private final long createdAtMillis = System.currentTimeMillis();
	private final AtomicInteger awaiting = new AtomicInteger();
	private volatile String origin = "unknown";

	private Future(CompletableFuture<Object> delegate) {
		this.delegate = delegate;
		FutureRegistry.track(this);
	}

	/** Where this future was created, as "script:line". Set once by the expression that made it. */
	public Future origin(String origin) {
		this.origin = origin;
		return this;
	}

	public String getOrigin() {
		return origin;
	}

	public long getAgeMillis() {
		return Math.max(0, System.currentTimeMillis() - createdAtMillis);
	}

	/** How many `wait for` sections are currently suspended on this future. */
	public int getAwaitingCount() {
		return awaiting.get();
	}

	public void awaitStarted() {
		awaiting.incrementAndGet();
	}

	public void awaitEnded() {
		awaiting.updateAndGet(n -> Math.max(0, n - 1));
	}

	/** The reason reported for a future failed without an explicit one. */
	public static final String DEFAULT_FAILURE_REASON = "future failed";

	/**
	 * Reported when a background lambda body errored. Skript catches runtime errors inside a trigger and
	 * logs them itself, so the underlying message is on the console rather than available to us here.
	 */
	public static final String BODY_ERROR_REASON = "the lambda body errored (see console for details)";

	/** An unresolved future you complete yourself (a promise). */
	public static Future pending() {
		return new Future(new CompletableFuture<>());
	}

	/** An already-resolved future. */
	public static Future completed(@Nullable Object value) {
		return new Future(CompletableFuture.completedFuture(value));
	}

	/** An already-failed future carrying {@code reason}. */
	public static Future failed(@Nullable String reason) {
		CompletableFuture<Object> cf = new CompletableFuture<>();
		cf.completeExceptionally(new FutureFailure(reason));
		return new Future(cf);
	}

	/** A future that runs {@code lambda} off the main thread and resolves with its result. The lambda must be thread-safe (no Bukkit API). */
	public static Future ofLambda(Lambda lambda, Object @NotNull [] args) {
		CompletableFuture<Object> cf = new CompletableFuture<>();
		pool().execute(() -> settle(cf, lambda, args));
		return new Future(cf);
	}

	/** Runs {@code lambda} and settles {@code cf} with its result, or fails it if the body errored or threw. */
	private static void settle(CompletableFuture<Object> cf, Lambda lambda, Object @NotNull [] args) {
		try {
			Lambda.Outcome outcome = lambda.call(args);
			if (outcome.errored()) {
				cf.completeExceptionally(new FutureFailure(BODY_ERROR_REASON));
			} else {
				cf.complete(outcome.value());
			}
		} catch (Throwable t) {
			cf.completeExceptionally(t);
		}
	}

	/** Narrows an arbitrary value to a Future, or null if it isn't one. */
	public static @Nullable Future from(@Nullable Object value) {
		return value instanceof Future future ? future : null;
	}

	public CompletableFuture<Object> raw() {
		return delegate;
	}

	/** Completes this future with {@code value} if it wasn't already resolved; returns whether it did. */
	public boolean complete(@Nullable Object value) {
		return delegate.complete(value);
	}

	/** Fails this future with {@code reason} if it wasn't already settled; returns whether it did. */
	public boolean fail(@Nullable String reason) {
		return delegate.completeExceptionally(new FutureFailure(reason));
	}

	/**
	 * Why this future failed, or null if it is pending or resolved successfully. Never null for a
	 * failed future, so `failure reason of` always reads for one.
	 */
	public @Nullable String failureReason() {
		if (!delegate.isCompletedExceptionally()) return null;
		try {
			delegate.getNow(null);
			return DEFAULT_FAILURE_REASON; // unreachable: it completed exceptionally.
		} catch (Throwable thrown) {
			return describe(thrown);
		}
	}

	/**
	 * A new future resolving with {@code lambda} applied to this one's result, computed on the shared
	 * pool. The source is untouched; if it fails, the result fails with the same reason and the lambda
	 * never runs.
	 */
	public Future mapped(Lambda lambda) {
		CompletableFuture<Object> out = new CompletableFuture<>();
		delegate.whenComplete((value, error) -> {
			if (error != null) {
				out.completeExceptionally(unwrap(error));
				return;
			}
			pool().execute(() -> settle(out, lambda, new Object[]{value}));
		});
		return new Future(out);
	}

	public boolean isDone() {
		return delegate.isDone();
	}

	public boolean isFailed() {
		return delegate.isCompletedExceptionally();
	}

	/** The resolved value, or null if the future isn't done yet or failed. */
	public @Nullable Object result() {
		if (!delegate.isDone() || delegate.isCompletedExceptionally()) return null;
		return delegate.getNow(null);
	}

	/** Strips the CompletionException/ExecutionException wrappers the JDK adds around a real cause. */
	private static Throwable unwrap(Throwable thrown) {
		Throwable root = thrown;
		while ((root instanceof CompletionException || root instanceof ExecutionException)
				&& root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root;
	}

	/** A human-readable reason for a throwable: an explicit `fail` reason, else the exception's message. */
	private static String describe(Throwable thrown) {
		Throwable root = unwrap(thrown);
		String message = root.getMessage();
		if (root instanceof FutureFailure) return message == null ? DEFAULT_FAILURE_REASON : message;
		// A background lambda threw: report its message, falling back to the type when it has none.
		return (message == null || message.isBlank()) ? root.getClass().getSimpleName() : message;
	}

	/** Carries an explicit `fail ... with` reason. Stackless: it is a control signal, not a bug report. */
	private static final class FutureFailure extends RuntimeException {

		FutureFailure(@Nullable String reason) {
			super(reason == null ? DEFAULT_FAILURE_REASON : reason, null, false, false);
		}

	}

	private static ExecutorService pool() {
		ExecutorService p = pool;
		if (p == null) {
			synchronized (Future.class) {
				p = pool;
				if (p == null) {
					int size = Math.max(2, Runtime.getRuntime().availableProcessors());
					p = Executors.newFixedThreadPool(size, runnable -> {
						Thread thread = new Thread(runnable, "skLambda-future-" + THREAD_COUNTER.incrementAndGet());
						thread.setDaemon(true);
						return thread;
					});
					pool = p;
				}
			}
		}
		return p;
	}

	/** Shuts the worker pool down; called on plugin disable. */
	public static void shutdownPool() {
		synchronized (Future.class) {
			if (pool != null) {
				pool.shutdownNow();
				pool = null;
			}
		}
	}

	@Override
	public String toString() {
		if (delegate.isCompletedExceptionally()) return "future (failed)";
		return delegate.isDone() ? "future (done)" : "future (pending)";
	}

	public static void registerType() {
		Classes.registerClass(new ClassInfo<>(Future.class, "future")
				.user("futures?")
				.name("Future")
				.description("An eventual result (a promise) you can store, complete, combine, and await with `wait for`.")
				.since("1.3.0")
				.parser(new Parser<>() {
					@Override
					public boolean canParse(@NotNull ParseContext context) {
						return false;
					}

					@Override
					public @NotNull String toString(Future future, int flags) {
						return future.toString();
					}

					@Override
					public @NotNull String toVariableNameString(Future future) {
						return future.toString();
					}
				}));

		// Two futures are the same only when they are the same object; without this Skript has no
		// comparator for the type and `contains` / `is` on a list of them always reads false.
		Comparators.registerComparator(Future.class, Future.class,
				(first, second) -> Relation.get(first == second));
	}

}
