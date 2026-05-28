package com.sklambda.elements.sections;

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
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import com.sklambda.elements.events.ListenerDetachedEvent;
import com.sklambda.elements.types.Listener;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Name("Listen Section")
@Description("""
		Declarative event listener.
		- `listen for <event> [where <cond>]:` registers immediately.
		- `set %~object% to listener for <event> [where <cond>]:` defines for later `register`.
		Body entries: `countdown: <timespan>`, `triggers: <number>`.
		Body sub-sections: `on trigger:`, `on completion:`, `on timeout:`.""")
@Example("""
		listen for block break where event-block is stone:
			countdown: 30 seconds
			triggers: 10
			on trigger:
				send "keep going..." to event-player
			on completion:
				send "you did it!" to event-player
			on timeout:
				send "too slow!" to event-player
		""")
@Since("0.0.1-alpha")
public class SecListen extends EffectSection {

	private static final Pattern WHERE_PATTERN = Pattern.compile("\\s+where\\s+(.+?)\\s*$");

	private static final ThreadLocal<Integer> INSIDE_ON_TRIGGER = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Boolean> SAW_COMPLETE = ThreadLocal.withInitial(() -> false);

	public static boolean isInsideOnTrigger() {
		return INSIDE_ON_TRIGGER.get() > 0;
	}

	public static void markSawComplete() {
		SAW_COMPLETE.set(true);
	}

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(SecListen.class)
				.supplier(SecListen::new)
				.addPatterns(
						"listen for <.+>",
						"set %~object% to listener for <.+>")
				.build());
	}

	private boolean autoRegister;
	private @Nullable Expression<?> target;
	private SkriptEvent skriptEvent;
	private Class<? extends Event>[] eventClasses;
	private Class<? extends Event> @Nullable [] outerEvents;
	private @Nullable Expression<? extends Timespan> countdownExpr;
	private @Nullable Expression<? extends Number> triggersExpr;
	private @Nullable Condition filter;
	private @Nullable Trigger onTrigger;
	private @Nullable Trigger onCompletion;
	private @Nullable Trigger onTimeout;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		if (!hasSection() || sectionNode == null) {
			Skript.error("`listen` requires a section body — `on trigger:` / `on completion:` / `on timeout:`.");
			return false;
		}
		autoRegister = matchedPattern == 0;
		if (!autoRegister) {
			target = exprs[0];
			if (!(target instanceof Variable<?>)) {
				Skript.error("Listener target must be a variable.");
				return false;
			}
		}

		String full = parseResult.regexes.get(0).group();
		String whereText = null;
		Matcher m = WHERE_PATTERN.matcher(full);
		if (m.find()) {
			whereText = m.group(1).trim();
			full = full.substring(0, m.start());
		}
		String eventPattern = full.trim();

		skriptEvent = SkriptEvent.parse(eventPattern, sectionNode, "Unrecognized event pattern: " + eventPattern);
		if (skriptEvent == null) return false;
		eventClasses = skriptEvent.getEventClasses();

		ParserInstance parser = getParser();
		outerEvents = parser.getCurrentEvents();
		String prevEventName = parser.getCurrentEventName();

		if (whereText != null) {
			parser.setCurrentEvent("listen filter", eventClasses);
			try {
				filter = Condition.parse(whereText, "Invalid `where` condition: " + whereText);
			} finally {
				if (outerEvents != null) {
					parser.setCurrentEvent(prevEventName, outerEvents);
				} else {
					parser.deleteCurrentEvent();
				}
			}
			if (filter == null) return false;
		}

		SectionNode trig = null, comp = null, tout = null;
		boolean anyChild = false;
		for (Node child : sectionNode) {
			anyChild = true;
			if (child instanceof SimpleNode) {
				String text = child.getKey() == null ? "" : child.getKey();
				int colon = text.indexOf(':');
				if (colon < 0) {
					Skript.error("Expected `key: value` entry or sub-section inside `listen`, got: " + text);
					return false;
				}
				String key = text.substring(0, colon).trim().toLowerCase();
				String value = text.substring(colon + 1).trim();
				switch (key) {
					case "countdown" -> {
						if (countdownExpr != null) { Skript.error("Duplicate `countdown:` entry."); return false; }
						countdownExpr = parseTimespan(value);
						if (countdownExpr == null) return false;
					}
					case "triggers" -> {
						if (triggersExpr != null) { Skript.error("Duplicate `triggers:` entry."); return false; }
						triggersExpr = parseNumber(value);
						if (triggersExpr == null) return false;
					}
					default -> {
						Skript.error("Unknown entry `" + key + ":` inside `listen` — expected `countdown:` or `triggers:`.");
						return false;
					}
				}
			} else if (child instanceof SectionNode subNode) {
				String key = subNode.getKey() == null ? "" : subNode.getKey().trim().toLowerCase();
				switch (key) {
					case "on trigger" -> {
						if (trig != null) { Skript.error("Duplicate `on trigger` block."); return false; }
						trig = subNode;
					}
					case "on completion" -> {
						if (comp != null) { Skript.error("Duplicate `on completion` block."); return false; }
						comp = subNode;
					}
					case "on timeout" -> {
						if (tout != null) { Skript.error("Duplicate `on timeout` block."); return false; }
						tout = subNode;
					}
					default -> {
						Skript.error("Unknown block `" + key + "` inside `listen` — expected `on trigger`, `on completion`, or `on timeout`.");
						return false;
					}
				}
			} else {
				Skript.error("Unexpected line inside `listen` block.");
				return false;
			}
		}
		if (!anyChild) {
			Skript.error("`listen` requires a body (entries or sub-sections).");
			return false;
		}
		if (tout != null && countdownExpr == null) {
			Skript.error("`on timeout` requires a `countdown:` entry.");
			return false;
		}

		Class<? extends Event>[] triggerEvents = eventClassesOrDefault();
		if (trig != null) {
			int prevDepth = INSIDE_ON_TRIGGER.get();
			boolean prevSaw = SAW_COMPLETE.get();
			INSIDE_ON_TRIGGER.set(prevDepth + 1);
			SAW_COMPLETE.set(false);
			try {
				onTrigger = loadCode(trig, "listen on trigger", triggerEvents);
				if (onTrigger == null) return false;
				if (comp != null && triggersExpr == null && !SAW_COMPLETE.get()) {
					Skript.warning("`on completion` may never run — no `triggers:` target and no `unregister listener` call in `on trigger`.");
				}
			} finally {
				INSIDE_ON_TRIGGER.set(prevDepth);
				SAW_COMPLETE.set(prevSaw);
			}
		}
		if (comp != null) {
			onCompletion = loadCode(comp, "listen on completion", triggerEvents);
			if (onCompletion == null) return false;
		}
		if (tout != null) {
			onTimeout = loadCode(tout, "listen on timeout", timeoutEventClasses());
			if (onTimeout == null) return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Event>[] eventClassesOrDefault() {
		return (Class<? extends Event>[]) new Class<?>[]{eventClasses.length > 0 ? eventClasses[0] : ListenerDetachedEvent.class};
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Event>[] timeoutEventClasses() {
		LinkedHashSet<Class<? extends Event>> set = new LinkedHashSet<>();
		if (eventClasses.length > 0) set.add(eventClasses[0]);
		if (outerEvents != null) Collections.addAll(set, outerEvents);
		if (set.isEmpty()) set.add(ListenerDetachedEvent.class);
		return set.toArray((Class<? extends Event>[]) new Class<?>[0]);
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Expression<? extends Timespan> parseTimespan(String text) {
		Expression<?> e = new SkriptParser(text, SkriptParser.ALL_FLAGS).parseExpression(Timespan.class);
		if (e == null) {
			Skript.error("Expected a timespan (e.g. 30 seconds), got: " + text);
			return null;
		}
		return (Expression<? extends Timespan>) e;
	}

	@SuppressWarnings("unchecked")
	private static @Nullable Expression<? extends Number> parseNumber(String text) {
		Expression<?> e = new SkriptParser(text, SkriptParser.ALL_FLAGS).parseExpression(Number.class);
		if (e == null) {
			Skript.error("Expected a number, got: " + text);
			return null;
		}
		return (Expression<? extends Number>) e;
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		int targetCount = triggersExpr != null ? toInt(triggersExpr.getSingle(event)) : 0;
		long delayTicks = -1;
		if (countdownExpr != null) {
			Timespan ts = countdownExpr.getSingle(event);
			if (ts != null) delayTicks = Math.max(1, ts.getAs(Timespan.TimePeriod.TICK));
		}

		Listener listener = new Listener(skriptEvent, eventClasses, filter, onTrigger, onCompletion, onTimeout, targetCount, delayTicks);
		listener.captureFrom(event);

		if (autoRegister) {
			listener.register();
		} else {
			this.target.change(event, new Object[]{listener}, ChangeMode.SET);
		}
		return walk(event, false);
	}

	private static int toInt(@Nullable Number n) {
		return n == null ? 0 : n.intValue();
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String base = "listen for " + skriptEvent.toString(event, debug);
		return autoRegister ? base : "set " + target.toString(event, debug) + " to " + base;
	}

}
