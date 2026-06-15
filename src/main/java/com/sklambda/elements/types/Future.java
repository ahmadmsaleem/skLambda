package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A first-class value wrapping a {@link CompletableFuture}: an eventual result you can store, pass,
 * complete, combine, and await. Backed by a shared daemon thread pool for off-thread lambda calls.
 */
public final class Future {

	private static final AtomicLong THREAD_COUNTER = new AtomicLong();
	private static volatile @Nullable ExecutorService pool;

	private final CompletableFuture<Object> delegate;

	private Future(CompletableFuture<Object> delegate) {
		this.delegate = delegate;
	}

	/** An unresolved future you complete yourself (a promise). */
	public static Future pending() {
		return new Future(new CompletableFuture<>());
	}

	/** A future that runs {@code lambda} off the main thread and resolves with its result. The lambda must be thread-safe (no Bukkit API). */
	public static Future ofLambda(Lambda lambda, Object @NotNull [] args) {
		CompletableFuture<Object> cf = new CompletableFuture<>();
		pool().execute(() -> {
			try {
				cf.complete(lambda.invoke(args));
			} catch (Throwable t) {
				cf.completeExceptionally(t);
			}
		});
		return new Future(cf);
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
	}

}
