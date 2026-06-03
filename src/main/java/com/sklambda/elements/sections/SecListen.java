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
import ch.njol.skript.variables.HintManager;
import ch.njol.util.Kleenean;
import com.sklambda.elements.events.ListenerDetachedEvent;
import com.sklambda.elements.types.Listener;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Name("Listen Section")
@Description({
		"Declarative event listener. Two forms are available:",
		"\t`  - listen for event [where cond]:` registers immediately.",
		"\t`  - set %~object% to listener for event [where cond]:` defines the listener for later activation via `register`.",
		"\t",
		"Optional entries:",
		"\t`  - countdown: timespan` sets an auto-timeout duration. Required when `on timeout:` is used.",
		"\t`  - triggers: number` caps how many times the listener fires before `on completion:` runs.",
		"\t`  - cooldown: timespan` debounces the listener: after an accepted trigger, further events within this window are ignored — they don't run `on trigger:` and don't count toward `triggers:`. Per-listener, so scope it to a player (via `owner:`/`where`) for a per-player cooldown.",
		"\t`  - owner: offlineplayer/entity/chunk/world` scopes the listener to that owner and auto-unregisters when the owner goes away: a player disconnecting, an entity being removed from the world (death/despawn), or a chunk/world unloading. Any owner can be bulk-cleaned with `unregister all listeners owned by %object%`.",
		"\t",
		"Optional sections:",
		"\t`  - where:` adds extra filter conditions that must all pass. Combines with any inline `where` clause.",
		"\t`  - every timespan:` runs its body on a repeating timer while the listener is active (and is paused along with the listener). Useful for live displays, e.g. an action-bar countdown.",
		"\t`  - on trigger:` runs each time the event fires after filters pass. Inside it, `cancel listener` stops early without firing completion or timeout, and `skip trigger` ignores the current event without consuming a `triggers:` slot.",
		"\t`  - on completion:` runs once `triggers:` is reached.",
		"\t`  - on timeout:` runs when `countdown:` elapses.",
		"\t`  - on end:` runs whenever the listener stops, however it stops — after completion, after timeout, and on `cancel listener`/`unregister`/owner-disconnect cleanup. Use it for teardown; `end reason` tells you why it stopped.",
		"\t`  - on pause:` runs each time the listener is paused (via `pause %listener%`), after the countdown freezes.",
		"\t`  - on resume:` runs each time the listener is resumed (via `resume %listener%`), after the countdown restarts.",
			"\t`  - on register:` runs once the listener becomes active (immediately for `listen for ...:`, or when `register %listener%` activates a declared one), symmetric with `on end:` for setup/teardown.",
		"\t",
		"Expressions valid inside any callback:",
		"\t`  - remaining triggers` reports the fires left before completion.",
		"\t`  - remaining countdown` reports the time left before timeout.",
		"\t`  - end reason` (inside `on end:`) is `completion`, `timeout`, `cancelled`, or `unregistered`."
})
@Example("""
		listen for block break where event-block is stone:
			where:
				event-player is sneaking
			countdown: 30 seconds
			triggers: 10
			cooldown: 1 second
			on trigger:
				if event-world is not "world":
					skip trigger
				send "keep going... (%remaining triggers% left)" to event-player
			every 5 seconds:
				send action bar "%remaining countdown% left" to event-player
			on completion:
				send "you did it!" to event-player
			on timeout:
				send "too slow! (%remaining triggers% left to break)" to event-player
			on pause:
				send "challenge paused (%remaining countdown% left)" to event-player
			on resume:
				send "challenge resumed!" to event-player
			on end:
				send "challenge over (%end reason%)" to event-player
		""")
@Since("0.0.1-alpha")
public class SecListen extends EffectSection {

	private static final Pattern WHERE_PATTERN = Pattern.compile("\\s+where\\s+(.+?)\\s*$");

	private static final ThreadLocal<Integer> INSIDE_ON_TRIGGER = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Integer> INSIDE_LISTEN_CALLBACK = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Boolean> SAW_COMPLETE = ThreadLocal.withInitial(() -> false);

	public static boolean isInsideOnTrigger() {
		return INSIDE_ON_TRIGGER.get() > 0;
	}

	public static boolean isInsideListenCallback() {
		return INSIDE_LISTEN_CALLBACK.get() > 0;
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
	private String sourceLocation = "unknown";
	private String eventLabel = "";
	private SkriptEvent skriptEvent;
	private Class<? extends Event>[] eventClasses;
	private Class<? extends Event> @Nullable [] outerEvents;
	private @Nullable Expression<? extends Timespan> countdownExpr;
	private @Nullable Expression<? extends Number> triggersExpr;
	private @Nullable Expression<?> ownerExpr;
	private @Nullable Expression<? extends Timespan> cooldownExpr;
	private @Nullable Expression<? extends Timespan> tickExpr;
	private final List<Condition> filters = new ArrayList<>();
	private @Nullable Trigger onTrigger;
	private @Nullable Trigger onCompletion;
	private @Nullable Trigger onTimeout;
	private @Nullable Trigger onEnd;
	private @Nullable Trigger onTick;
	private @Nullable Trigger onPause;
	private @Nullable Trigger onResume;
	private @Nullable Trigger onRegister;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		if (!hasSection() || sectionNode == null) {
			Skript.error("listen requires a section body, on trigger: / on completion: / on timeout:.");
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
		eventLabel = eventPattern;
		String fileName = sectionNode.getConfig().getFileName();
		int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
		sourceLocation = (slash >= 0 ? fileName.substring(slash + 1) : fileName) + ":" + sectionNode.getLine();

		skriptEvent = SkriptEvent.parse(eventPattern, sectionNode, "Unrecognized event pattern: " + eventPattern);
		if (skriptEvent == null) return false;
		eventClasses = skriptEvent.getEventClasses();

		ParserInstance parser = getParser();
		outerEvents = parser.getCurrentEvents();
		String prevEventName = parser.getCurrentEventName();

		if (whereText != null) {
			String text = whereText;
			boolean ok = withFilterEvent(parser, prevEventName, () -> {
				Condition inlineFilter = Condition.parse(text, "Invalid `where` condition: " + text);
				if (inlineFilter == null) return false;
				filters.add(inlineFilter);
				return true;
			});
			if (!ok) return false;
		}

		SectionNode trig = null, comp = null, tout = null, end = null, tick = null, pause = null, resume = null, reg = null;
		boolean anyChild = false;
		for (Node child : sectionNode) {
			anyChild = true;
			if (child instanceof SimpleNode) {
				String text = child.getKey() == null ? "" : child.getKey();
				int colon = text.indexOf(':');
				if (colon < 0) {
					Skript.error("Expected key: value entry or sub-section inside listen, got: " + text);
					return false;
				}
				String key = text.substring(0, colon).trim().toLowerCase();
				String value = text.substring(colon + 1).trim();
				switch (key) {
					case "countdown" -> {
						if (countdownExpr != null) { Skript.error("Duplicate countdown: entry."); return false; }
						countdownExpr = parseTimespan(value);
						if (countdownExpr == null) return false;
					}
					case "triggers" -> {
						if (triggersExpr != null) { Skript.error("Duplicate triggers: entry."); return false; }
						triggersExpr = parseNumber(value);
						if (triggersExpr == null) return false;
					}
					case "owner" -> {
						if (ownerExpr != null) { Skript.error("Duplicate owner: entry."); return false; }
						ownerExpr = parseOwner(value);
						if (ownerExpr == null) return false;
					}
					case "cooldown" -> {
						if (cooldownExpr != null) { Skript.error("Duplicate cooldown: entry."); return false; }
						cooldownExpr = parseTimespan(value);
						if (cooldownExpr == null) return false;
					}
					default -> {
						Skript.error("Unknown entry " + key + ": inside listen expected countdown:, triggers:, cooldown:, or owner:.");
						return false;
					}
				}
			} else if (child instanceof SectionNode subNode) {
				String key = subNode.getKey() == null ? "" : subNode.getKey().trim().toLowerCase();
				if (key.startsWith("every ")) {
					if (tick != null) { Skript.error("Duplicate every <timespan>: block."); return false; }
					String interval = subNode.getKey().trim().substring("every".length()).trim();
					tickExpr = parseTimespan(interval);
					if (tickExpr == null) return false;
					tick = subNode;
					continue;
				}
				switch (key) {
					case "where" -> {
						SectionNode whereNode = subNode;
						boolean ok = withFilterEvent(parser, prevEventName, () -> {
							for (Node line : whereNode) {
								if (!(line instanceof SimpleNode)) {
									Skript.error("where: only accepts condition lines.");
									return false;
								}
								String text = line.getKey() == null ? "" : line.getKey().trim();
								if (text.isEmpty()) continue;
								Condition c = Condition.parse(text, "Invalid where condition: " + text);
								if (c == null) return false;
								filters.add(c);
							}
							return true;
						});
						if (!ok) return false;
					}
					case "on trigger" -> {
						if (trig != null) { Skript.error("Duplicate on trigger block."); return false; }
						trig = subNode;
					}
					case "on completion" -> {
						if (comp != null) { Skript.error("Duplicate on completion block."); return false; }
						comp = subNode;
					}
					case "on timeout" -> {
						if (tout != null) { Skript.error("Duplicate on timeout block."); return false; }
						tout = subNode;
					}
					case "on end" -> {
						if (end != null) { Skript.error("Duplicate on end block."); return false; }
						end = subNode;
					}
					case "on pause" -> {
						if (pause != null) { Skript.error("Duplicate on pause block."); return false; }
						pause = subNode;
					}
					case "on resume" -> {
						if (resume != null) { Skript.error("Duplicate on resume block."); return false; }
						resume = subNode;
					}
					case "on register" -> {
						if (reg != null) { Skript.error("Duplicate on register block."); return false; }
						reg = subNode;
					}
					default -> {
						Skript.error("Unknown block " + key + " inside listen, expected where, on trigger, on completion, on timeout, on end, on register, on pause, on resume, or every <timespan>.");
						return false;
					}
				}
			} else {
				Skript.error("Unexpected line inside listen block.");
				return false;
			}
		}
		if (!anyChild) {
			Skript.error("listen requires a body (entries or sub-sections).");
			return false;
		}
		if (tout != null && countdownExpr == null) {
			Skript.error("on timeout requires a countdown: entry.");
			return false;
		}

		Class<? extends Event>[] triggerEvents = eventClassesOrDefault();
		int prevCb = INSIDE_LISTEN_CALLBACK.get();
		INSIDE_LISTEN_CALLBACK.set(prevCb + 1);
		try {
			if (trig != null) {
				int prevDepth = INSIDE_ON_TRIGGER.get();
				boolean prevSaw = SAW_COMPLETE.get();
				INSIDE_ON_TRIGGER.set(prevDepth + 1);
				SAW_COMPLETE.set(false);
				try {
					onTrigger = loadCode(trig, "listen on trigger", triggerEvents);
					if (onTrigger == null) return false;
					if (comp != null && triggersExpr == null && !SAW_COMPLETE.get()) {
						Skript.warning("`on completion` may never run, no triggers: target and no `unregister listener` call in `on trigger`.");
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
			if (end != null) {
				onEnd = loadCode(end, "listen on end", timeoutEventClasses());
				if (onEnd == null) return false;
			}
			if (tick != null) {
				onTick = loadCode(tick, "listen every", timeoutEventClasses());
				if (onTick == null) return false;
			}
			if (pause != null) {
				onPause = loadCode(pause, "listen on pause", timeoutEventClasses());
				if (onPause == null) return false;
			}
			if (resume != null) {
				onResume = loadCode(resume, "listen on resume", timeoutEventClasses());
				if (onResume == null) return false;
			}
			if (reg != null) {
				onRegister = loadCode(reg, "listen on register", timeoutEventClasses());
				if (onRegister == null) return false;
			}
		} finally {
			INSIDE_LISTEN_CALLBACK.set(prevCb);
		}

		// Record a local-variable type hint so Skript knows this variable holds a listener.
		// No-op unless the script enables Skript's experimental `using type hints`.
		if (!autoRegister && target instanceof Variable<?> var && HintManager.canUseHints(var)) {
			getParser().getHintManager().set(var, Listener.class);
		}
		return true;
	}

	private boolean withFilterEvent(ParserInstance parser, @Nullable String prevEventName, BooleanSupplier body) {
		parser.setCurrentEvent("listen filter", eventClasses);
		try {
			return body.getAsBoolean();
		} finally {
			if (outerEvents != null) {
				parser.setCurrentEvent(prevEventName, outerEvents);
			} else {
				parser.deleteCurrentEvent();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Event>[] eventClassesOrDefault() {
		return eventClasses.length > 0
				? eventClasses
				: (Class<? extends Event>[]) new Class<?>[]{ListenerDetachedEvent.class};
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

	private static @Nullable Expression<?> parseOwner(String text) {
		Expression<?> e = new SkriptParser(text, SkriptParser.ALL_FLAGS)
				.parseExpression(OfflinePlayer.class, Entity.class, Chunk.class, World.class);
		if (e == null) {
			Skript.error("Expected an owner — an offline player, entity, chunk, or world — but got: " + text);
			return null;
		}
		return e;
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		int targetCount = triggersExpr != null ? toInt(triggersExpr.getSingle(event)) : 0;
		long delayTicks = -1;
		if (countdownExpr != null) {
			Timespan ts = countdownExpr.getSingle(event);
			if (ts != null) delayTicks = Math.max(1, ts.getAs(Timespan.TimePeriod.TICK));
		}
		long tickTicks = 0;
		if (tickExpr != null) {
			Timespan ts = tickExpr.getSingle(event);
			if (ts != null) tickTicks = Math.max(1, ts.getAs(Timespan.TimePeriod.TICK));
		}
		long cooldownMs = 0;
		if (cooldownExpr != null) {
			Timespan ts = cooldownExpr.getSingle(event);
			if (ts != null) cooldownMs = Math.max(0, ts.getAs(Timespan.TimePeriod.MILLISECOND));
		}

		Object owner = ownerExpr != null ? ownerExpr.getSingle(event) : null;
		Listener listener = Listener.builder(skriptEvent, eventClasses)
				.filters(filters)
				.onTrigger(onTrigger)
				.onCompletion(onCompletion)
				.onTimeout(onTimeout)
				.onEnd(onEnd)
				.onTick(onTick)
				.onPause(onPause)
				.onResume(onResume)
				.onRegister(onRegister)
				.triggers(targetCount)
				.timeoutTicks(delayTicks)
				.tickIntervalTicks(tickTicks)
				.cooldownMillis(cooldownMs)
				.sourceLocation(sourceLocation)
				.eventLabel(eventLabel)
				.owner(owner)
				.build();
		listener.captureFrom(event);

		if (autoRegister) {
			listener.register();
		} else {
			if (this.target.getSingle(event) instanceof Listener oldListener) {
				oldListener.unregister();
			}
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
