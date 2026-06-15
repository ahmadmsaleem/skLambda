package com.sklambda.elements.sections;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.config.SimpleNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.EffectSection;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.HintManager;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.List;
import java.util.function.Function;

@Name("Watch Section (Listener)")
@Description({
		"Polls a value on a timer and runs `on change:` whenever it differs from the last poll (change detection built in).",
		"\t`watch %value% every %timespan%:` re-evaluates the expression each interval.",
		"\t`watch %lambda% for %arguments% every %timespan%:` calls the lambda with those arguments each interval.",
		"\tInside `on change:`, `old value` and `new value` hold the previous and current values.",
		"\tOptional entry: `owner: %offlineplayer/entity/chunk/world%` auto-stops the watcher when the owner goes away.",
		"\tOptional blocks: `on timeout:` (needs `within %timespan%`) and `on end:` (runs whenever the watcher stops, for teardown).",
		"\tThe result is a normal listener handle: `pause`, `resume`, `unregister`, `is registered`, and `/sklambda listeners` all work. Declare one with `set %~object% to a watcher on ...` then `register` it, or use the bare `watch ...` form to start immediately.",
		"\tThe value is read on the main thread, so the watched expression/lambda must be safe there (the usual Skript rule)."
})
@Example("""
		set {_p} to sender
		watch (balance of {_p}) every 2 seconds within 5 minutes:
			on change:
				send "balance: %old value% -> %new value%" to {_p}
			on timeout:
				send "stopped watching your balance" to {_p}
		""")
@Since("1.3.0")
public class SecWatch extends EffectSection {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(SecWatch.class)
				.supplier(SecWatch::new)
				.addPatterns(
						"watch %object% [(with|for) %-objects%] every %timespan% [(within|timeout after) %-timespan%]",
						"set %~object% to [a|the] watcher (on|of|over) %object% [(with|for) %-objects%] every %timespan% [(within|timeout after) %-timespan%]")
				.build());
	}

	private boolean autoRegister;
	private @Nullable Expression<?> target;
	private Expression<?> sourceExpr;
	private @Nullable Expression<?> argsExpr;
	private Expression<? extends Timespan> intervalExpr;
	private @Nullable Expression<? extends Timespan> timeoutExpr;
	private Function<Event, Object> poller;
	private @Nullable Expression<?> ownerExpr;
	private String sourceLocation = "unknown";
	private String watchLabel = "value";
	private @Nullable Trigger onChange;
	private @Nullable Trigger onTimeout;
	private @Nullable Trigger onEnd;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		if (!hasSection() || sectionNode == null) {
			Skript.error("`watch` requires a body with an `on change:` block.");
			return false;
		}
		autoRegister = matchedPattern == 0;
		int base = autoRegister ? 0 : 1;
		if (!autoRegister) {
			target = exprs[0];
			if (!(target instanceof Variable<?>)) {
				Skript.error("Watcher target must be a variable.");
				return false;
			}
		}
		sourceExpr = exprs[base];
		if (exprs[base + 1] != null) {
			argsExpr = LiteralUtils.defendExpression(exprs[base + 1]);
			if (!LiteralUtils.canInitSafely(argsExpr)) return false;
		}
		intervalExpr = (Expression<? extends Timespan>) exprs[base + 2];
		timeoutExpr = (Expression<? extends Timespan>) exprs[base + 3];

		try {
			watchLabel = sourceExpr.toString(null, false);
		} catch (RuntimeException ignored) {
			watchLabel = "value";
		}
		sourceLocation = SectionSupport.sourceLocation(sectionNode);

		poller = buildPoller();

		// Parse the body under the surrounding event: `owner:`/`triggers:` entries plus `on ...:` blocks.
		ParserInstance parser = getParser();
		Class<? extends Event>[] outerEvents = parser.getCurrentEvents();
		Class<? extends Event>[] bodyEvents = (outerEvents != null && outerEvents.length > 0)
				? outerEvents : (Class<? extends Event>[]) new Class<?>[0];

		SectionNode changeNode = null, timeoutNode = null, endNode = null;
		for (Node child : sectionNode) {
			if (child instanceof SimpleNode) {
				String text = child.getKey() == null ? "" : ScriptLoader.replaceOptions(child.getKey());
				int colon = text.indexOf(':');
				if (colon < 0) {
					Skript.error("Expected a key: value entry or an `on ...:` block inside watch, got: " + text);
					return false;
				}
				String key = text.substring(0, colon).trim().toLowerCase();
				String value = text.substring(colon + 1).trim();
				if (key.equals("owner")) {
					if (ownerExpr != null) { Skript.error("Duplicate owner: entry."); return false; }
					ownerExpr = SectionSupport.parseOwner(value);
					if (ownerExpr == null) return false;
				} else {
					Skript.error("Unknown entry " + key + ": inside watch, expected owner:.");
					return false;
				}
			} else if (child instanceof SectionNode subNode) {
				String key = subNode.getKey() == null ? "" : subNode.getKey().trim().toLowerCase();
				switch (key) {
					case "on change" -> {
						if (changeNode != null) { Skript.error("Duplicate `on change` block."); return false; }
						changeNode = subNode;
					}
					case "on timeout" -> {
						if (timeoutNode != null) { Skript.error("Duplicate `on timeout` block."); return false; }
						timeoutNode = subNode;
					}
					case "on end" -> {
						if (endNode != null) { Skript.error("Duplicate `on end` block."); return false; }
						endNode = subNode;
					}
					default -> {
						Skript.error("Unknown block inside `watch`, expected on change, on timeout, or on end.");
						return false;
					}
				}
			} else {
				Skript.error("Unexpected line inside watch block.");
				return false;
			}
		}
		if (changeNode == null) {
			Skript.error("`watch` requires an `on change:` block.");
			return false;
		}
		if (timeoutNode != null && timeoutExpr == null) {
			Skript.error("`on timeout` requires a timeout, e.g. `... within 5 minutes:`.");
			return false;
		}

		// Parse callbacks in a listen-callback scope so `end reason` / `remaining countdown` resolve inside them.
		SecListen.pushListenCallback();
		try {
			onChange = loadCode(changeNode, "watch change", bodyEvents);
			if (onChange == null) return false;
			if (timeoutNode != null) {
				onTimeout = loadCode(timeoutNode, "watch timeout", bodyEvents);
				if (onTimeout == null) return false;
			}
			if (endNode != null) {
				onEnd = loadCode(endNode, "watch end", bodyEvents);
				if (onEnd == null) return false;
			}
		} finally {
			SecListen.popListenCallback();
		}

		if (!autoRegister && target instanceof Variable<?> var && HintManager.canUseHints(var)) {
			getParser().getHintManager().set(var, Listener.class);
		}
		return true;
	}

	private Function<Event, Object> buildPoller() {
		Expression<?> source = sourceExpr;
		Expression<?> args = argsExpr;
		if (args != null) {
			// Lambda form: call the lambda with the given arguments each poll.
			return event -> {
				Lambda lambda = Lambda.from(source.getSingle(event));
				return lambda == null ? null : lambda.invoke(args.getArray(event));
			};
		}
		// Expression form: re-evaluate the source each poll.
		return source::getSingle;
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		long intervalTicks = 1;
		Timespan interval = intervalExpr.getSingle(event);
		if (interval != null) intervalTicks = Math.max(1, interval.getAs(Timespan.TimePeriod.TICK));

		long timeoutTicks = -1;
		if (timeoutExpr != null) {
			Timespan ts = timeoutExpr.getSingle(event);
			if (ts != null) timeoutTicks = Math.max(1, ts.getAs(Timespan.TimePeriod.TICK));
		}

		Object owner = ownerExpr != null ? ownerExpr.getSingle(event) : null;

		@SuppressWarnings("unchecked")
		Class<? extends Event>[] noEvents = (Class<? extends Event>[]) new Class<?>[0];
		Listener watcher = Listener.builder(null, noEvents)
				.watchPoller(poller)
				.onChange(onChange)
				.onTimeout(onTimeout)
				.onEnd(onEnd)
				.owner(owner)
				.tickIntervalTicks(intervalTicks)
				.timeoutTicks(timeoutTicks)
				.sourceLocation(sourceLocation)
				.eventLabel(watchLabel)
				.build();
		watcher.captureFrom(event);

		if (autoRegister) {
			watcher.register();
		} else {
			if (target.getSingle(event) instanceof Listener old) old.unregister();
			target.change(event, new Object[]{watcher}, ChangeMode.SET);
		}
		return walk(event, false);
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String base = "watch " + sourceExpr.toString(event, debug);
		return autoRegister ? base : "set " + target.toString(event, debug) + " to a watcher on " + sourceExpr.toString(event, debug);
	}

}
