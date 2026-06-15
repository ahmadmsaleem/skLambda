package com.sklambda.elements.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.EffectSection;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import com.sklambda.SkLambda;
import com.sklambda.elements.types.Future;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Name("Await Future")
@Description({
		"Suspends the trigger until one or more futures resolve (or an optional timeout elapses), then continues. The server keeps ticking while the trigger waits: it suspends rather than blocking.",
		"\t`wait for %future% [(within|for at most) %timespan%]:` waits for a single future.",
		"\t`wait for all of %futures% [...]:` waits until every future resolves (when-all).",
		"\t`wait for any of %futures% [...]:` waits until the first one resolves (when-any).",
		"\tThe section body runs once the wait resolves; read values with `result of %future%` (the future is resolved by then). An optional `on timeout:` block, nested inside, runs instead if the timeout elapses. Locals set in either branch carry forward to the code after the block."
})
@Example("""
		set {_f} to future of calling lambda {_slow} with {_p}
		# ... other work runs while it computes ...
		wait for {_f} for at most 5 seconds:
			set {_r} to result of {_f}
			send "got %{_r}%" to {_p}
		on timeout:
			send "timed out" to {_p}
		""")
@Since("1.3.0")
public class SecAwaitFuture extends EffectSection {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(SecAwaitFuture.class)
				.supplier(SecAwaitFuture::new)
				.addPatterns(
						"wait for %future% [(within|for at most|for up to) %-timespan%]",
						"wait for (all|every|both) of %futures% [(within|for at most|for up to) %-timespan%]",
						"wait for any of %futures% [(within|for at most|for up to) %-timespan%]")
				.build());
	}

	private static final int ANY = 2;

	private Expression<?> futuresExpr;
	private @Nullable Expression<? extends Timespan> timeoutExpr;
	private boolean any;
	private @Nullable Trigger receivedTrigger;
	private @Nullable Trigger timeoutTrigger;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		if (!hasSection() || sectionNode == null) {
			Skript.error("`wait for` requires a body: the code to run once the future resolves.");
			return false;
		}
		futuresExpr = exprs[0];
		timeoutExpr = (Expression<? extends Timespan>) exprs[1];
		any = matchedPattern == ANY;

		// Split an optional `on timeout:` child out of the body; everything else is the received handler.
		SectionNode timeoutNode = null;
		for (Node child : sectionNode) {
			if (child instanceof SectionNode subNode) {
				String key = subNode.getKey() == null ? "" : subNode.getKey().trim().toLowerCase();
				if (key.equals("on timeout")) {
					if (timeoutNode != null) {
						Skript.error("Duplicate `on timeout` block.");
						return false;
					}
					timeoutNode = subNode;
				}
			}
		}
		if (timeoutNode != null && timeoutExpr == null) {
			Skript.error("`on timeout` requires a timeout, e.g. `... for at most 5 seconds:`.");
			return false;
		}
		if (timeoutNode != null) timeoutNode.remove();

		ParserInstance parser = getParser();
		Class<? extends Event>[] outerEvents = parser.getCurrentEvents();
		Class<? extends Event>[] bodyEvents = (outerEvents != null && outerEvents.length > 0)
				? outerEvents : (Class<? extends Event>[]) new Class<?>[0];

		receivedTrigger = loadCode(sectionNode, "wait", bodyEvents);
		if (receivedTrigger == null) return false;
		if (timeoutNode != null) {
			timeoutTrigger = loadCode(timeoutNode, "wait timeout", bodyEvents);
			if (timeoutTrigger == null) return false;
		}

		// Code after this block runs only once the wait resolves, so mark it delayed.
		parser.setHasDelayBefore(Kleenean.TRUE);
		return true;
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		debug(event, true);
		TriggerItem next = getNext();

		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return next;

		List<CompletableFuture<Object>> raws = new ArrayList<>();
		for (Object value : futuresExpr.getArray(event)) {
			Future future = Future.from(value);
			if (future != null) raws.add(future.raw());
		}

		long timeoutTicks = -1;
		if (timeoutExpr != null) {
			Timespan ts = timeoutExpr.getSingle(event);
			if (ts != null) timeoutTicks = Math.max(1, ts.getAs(Timespan.TimePeriod.TICK));
		}

		// Stash the trigger's locals across the wait, mirroring Skript's own Delay effect.
		Object localVars = Variables.removeLocals(event);
		AtomicBoolean resolved = new AtomicBoolean(false);
		BukkitTask[] timeoutHolder = new BukkitTask[1];

		CompletableFuture<?> gate;
		if (raws.isEmpty()) {
			gate = CompletableFuture.completedFuture(null);
		} else if (any) {
			gate = CompletableFuture.anyOf(raws.toArray(new CompletableFuture[0]));
		} else {
			gate = CompletableFuture.allOf(raws.toArray(new CompletableFuture[0]));
		}

		if (timeoutTicks > 0) {
			timeoutHolder[0] = Bukkit.getScheduler().runTaskLater(plugin,
					() -> finish(true, next, event, localVars, resolved, timeoutHolder, plugin), timeoutTicks);
		}
		gate.whenComplete((result, error) -> finish(false, next, event, localVars, resolved, timeoutHolder, plugin));

		// Suspend; finish() resumes the trigger when the wait resolves or times out.
		return null;
	}

	/** Resumes the suspended trigger once, on the main thread, replaying its locals and running the matching branch. */
	private void finish(boolean timedOut, @Nullable TriggerItem next, @NotNull Event event, @Nullable Object localVars,
						@NotNull AtomicBoolean resolved, @NotNull BukkitTask[] timeoutHolder, @NotNull Plugin plugin) {
		if (!resolved.compareAndSet(false, true)) return;
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (timeoutHolder[0] != null) timeoutHolder[0].cancel();
			if (localVars != null) Variables.setLocalVariables(event, localVars);
			Delay.addDelayedEvent(event);
			Trigger body = timedOut ? timeoutTrigger : receivedTrigger;
			// walk (not execute) so locals the body sets survive into the continuation.
			if (body != null) TriggerItem.walk(body, event);
			if (next != null) TriggerItem.walk(next, event);
			Variables.removeLocals(event);
		});
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "wait for " + (any ? "any of " : "") + futuresExpr.toString(event, debug);
	}

}
