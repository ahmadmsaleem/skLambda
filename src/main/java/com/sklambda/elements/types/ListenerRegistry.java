package com.sklambda.elements.types;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ListenerRegistry {

	private ListenerRegistry() {}

	private static final ThreadLocal<Deque<Listener>> CONTEXT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

	/** Set by `skip trigger`; read and reset by the listener after its `on trigger:` body runs. */
	public static final ThreadLocal<Boolean> SKIP_FLAG = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private static final Set<Listener> ACTIVE = Collections.synchronizedSet(new LinkedHashSet<>());
	private static final AtomicLong CREATION_COUNTER = new AtomicLong();

	static long nextCreationId() {
		return CREATION_COUNTER.incrementAndGet();
	}

	static void add(Listener listener) {
		ACTIVE.add(listener);
	}

	static void remove(Listener listener) {
		ACTIVE.remove(listener);
	}

	static void pushContext(Listener listener) {
		CONTEXT_STACK.get().push(listener);
	}

	static void popContext() {
		CONTEXT_STACK.get().pop();
	}

	public static @Nullable Listener currentContext() {
		return CONTEXT_STACK.get().peek();
	}

	public static List<Listener> activeListeners() {
		synchronized (ACTIVE) {
			return new ArrayList<>(ACTIVE);
		}
	}

	public static int unregisterAll() {
		int count = 0;
		for (Listener listener : activeListeners()) {
			if (listener.unregister()) count++;
		}
		return count;
	}

	/** Unregisters every active listener owned by {@code owner}; returns how many were stopped. */
	public static int unregisterAllOwnedBy(@Nullable Object owner) {
		if (owner == null || ACTIVE.isEmpty()) return 0;
		int count = 0;
		for (Listener listener : activeListeners()) {
			if (listener.isOwnedBy(owner) && listener.unregister()) count++;
		}
		return count;
	}

	/** the most recently created listener that is still active, or null. */
	public static @Nullable Listener lastCreated() {
		synchronized (ACTIVE) {
			Listener latest = null;
			for (Listener listener : ACTIVE) {
				if (latest == null || listener.creationId > latest.creationId) latest = listener;
			}
			return latest;
		}
	}

	/** warns once per {@code warnEveryMs} about each active listener alive longer than {@code warnAfterMs}. */
	public static void notifierScan(long warnAfterMs, long warnEveryMs, String template) {
		long now = System.currentTimeMillis();
		for (Listener listener : activeListeners()) {
			listener.maybeWarn(now, warnAfterMs, warnEveryMs, template);
		}
	}

	/** formats a duration in milliseconds as a compact "1h 2m 3s" string. */
	public static String formatDuration(long ms) {
		long totalSeconds = Math.max(0, ms) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		StringBuilder sb = new StringBuilder();
		if (hours > 0) sb.append(hours).append("h ");
		if (hours > 0 || minutes > 0) sb.append(minutes).append("m ");
		return sb.append(seconds).append('s').toString();
	}
}
