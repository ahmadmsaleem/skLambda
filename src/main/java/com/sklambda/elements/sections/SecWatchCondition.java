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
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.EffectSection;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.HintManager;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Name("Watch Condition (Listener)")
@Description({
		"Edge-triggered condition watcher: polls a condition on a timer and fires on the transition, not on every poll.",
		"\t`watch when <condition> every %timespan%:` with `on rising:` (the moment it goes false -> true) and/or `on falling:` (true -> false).",
		"\tThis is \"do X the moment Y first becomes true\" without writing a poll loop. The condition's state at the first poll is the baseline, so a transition is needed to fire (a condition already true at the start won't fire `on rising` until it goes false then true again).",
		"\tOptional entry: `owner: %offlineplayer/entity/chunk/world%` auto-stops the watcher when the owner goes away.",
		"\tOptional blocks: `on timeout:` (needs `within %timespan%`) and `on end:`.",
		"\tThe result is a normal listener handle: `pause`, `resume`, `unregister`, `is registered`, and `/sklambda listeners` all work. The condition is checked on the main thread."
})
@Example("""
		set {_p} to sender
		watch when (health of {_p} < 6) every 1 second within 10 minutes:
			on rising:
				send "<red>low health!" to {_p}
			on falling:
				send "<green>back to safe health" to {_p}
		""")
@Since("1.3.0")
public class SecWatchCondition extends EffectSection {

	private static final Pattern TIMEOUT_PATTERN =
			Pattern.compile("\\s+(?:within|timeout after)\\s+(.+?)\\s*$");
	private static final Pattern EVERY_PATTERN = Pattern.compile("\\s+every\\s+(.+?)\\s*$");

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(SecWatchCondition.class)
				.supplier(SecWatchCondition::new)
				.addPatterns(
						"watch (when|whether) <.+>",
						"set %~object% to [a|the] watcher (when|whether) <.+>")
				.build());
	}

	private boolean autoRegister;
	private @Nullable Expression<?> target;
	private Function<Event, Object> poller;
	private Expression<? extends Timespan> intervalExpr;
	private @Nullable Expression<? extends Timespan> timeoutExpr;
	private @Nullable Expression<?> ownerExpr;
	private String sourceLocation = "unknown";
	private String conditionLabel = "condition";
	private @Nullable Trigger onRising;
	private @Nullable Trigger onFalling;
	private @Nullable Trigger onTimeout;
	private @Nullable Trigger onEnd;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		if (!hasSection() || sectionNode == null) {
			Skript.error("`watch when` requires a body with an `on rising:` and/or `on falling:` block.");
			return false;
		}
		autoRegister = matchedPattern == 0;
		if (!autoRegister) {
			target = exprs[0];
			if (!(target instanceof Variable<?>)) {
				Skript.error("Watcher target must be a variable.");
				return false;
			}
		}

		String full = parseResult.regexes.get(0).group();
		String timeoutText = null;
		Matcher tm = TIMEOUT_PATTERN.matcher(full);
		if (tm.find()) {
			timeoutText = tm.group(1).trim();
			full = full.substring(0, tm.start());
		}
		Matcher em = EVERY_PATTERN.matcher(full);
		if (!em.find()) {
			Skript.error("`watch when` needs a poll interval, e.g. `... every 1 second:`.");
			return false;
		}
		String intervalText = em.group(1).trim();
		full = full.substring(0, em.start());
		String conditionText = full.trim();
		conditionLabel = conditionText;

		sourceLocation = SectionSupport.sourceLocation(sectionNode);

		// The condition (and interval/timeout) parse against the surrounding event, which is current here.
		Condition condition = Condition.parse(conditionText, "Invalid `watch when` condition: " + conditionText);
		if (condition == null) return false;
		poller = event -> condition.check(event);

		intervalExpr = SectionSupport.parseTimespan(intervalText);
		if (intervalExpr == null) return false;
		if (timeoutText != null) {
			timeoutExpr = SectionSupport.parseTimespan(timeoutText);
			if (timeoutExpr == null) return false;
		}

		ParserInstance parser = getParser();
		Class<? extends Event>[] outerEvents = parser.getCurrentEvents();
		Class<? extends Event>[] bodyEvents = (outerEvents != null && outerEvents.length > 0)
				? outerEvents : (Class<? extends Event>[]) new Class<?>[0];

		SectionNode risingNode = null, fallingNode = null, timeoutNode = null, endNode = null;
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
					case "on rising" -> {
						if (risingNode != null) { Skript.error("Duplicate `on rising` block."); return false; }
						risingNode = subNode;
					}
					case "on falling" -> {
						if (fallingNode != null) { Skript.error("Duplicate `on falling` block."); return false; }
						fallingNode = subNode;
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
						Skript.error("Unknown block inside `watch when`, expected on rising, on falling, on timeout, or on end.");
						return false;
					}
				}
			} else {
				Skript.error("Unexpected line inside watch block.");
				return false;
			}
		}
		if (risingNode == null && fallingNode == null) {
			Skript.error("`watch when` requires an `on rising:` and/or `on falling:` block.");
			return false;
		}
		if (timeoutNode != null && timeoutExpr == null) {
			Skript.error("`on timeout` requires a timeout, e.g. `... within 5 minutes:`.");
			return false;
		}

		// Parse callbacks in a listen-callback scope so `end reason` / `remaining countdown` resolve inside them.
		SecListen.pushListenCallback();
		try {
			if (risingNode != null) {
				onRising = loadCode(risingNode, "watch rising", bodyEvents);
				if (onRising == null) return false;
			}
			if (fallingNode != null) {
				onFalling = loadCode(fallingNode, "watch falling", bodyEvents);
				if (onFalling == null) return false;
			}
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
				.onRising(onRising)
				.onFalling(onFalling)
				.onTimeout(onTimeout)
				.onEnd(onEnd)
				.owner(owner)
				.tickIntervalTicks(intervalTicks)
				.timeoutTicks(timeoutTicks)
				.sourceLocation(sourceLocation)
				.eventLabel("when " + conditionLabel)
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
		String base = "watch when " + conditionLabel;
		return autoRegister ? base : "set " + target.toString(event, debug) + " to a watcher " + base;
	}

}
