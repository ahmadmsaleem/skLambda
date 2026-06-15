package com.sklambda.elements.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.EffectSection;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import com.sklambda.SkLambda;
import com.sklambda.elements.types.Listener;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Name("Await Section")
@Description({
		"Pauses the trigger until an event fires (or an optional timeout elapses), then continues with the code that follows.",
		"\tThe section body runs when the awaited event arrives, with that event's values in scope (e.g. `message` for `chat`).",
		"\tAn optional `on timeout:` block, nested inside the section alongside the body, runs instead if the timeout elapses; it runs in the original event's context (the awaited event never fired).",
		"\tWhichever branch runs, locals it sets carry forward, so the linear code after the block can read them.",
		"\t",
		"\t`wait for next <event> [where <condition>] [(within|for at most) <timespan>]:`",
		"\t",
		"\tFilter with a trailing `where` clause; the timeout clause comes last. Without a timeout the await waits indefinitely, so it will show in `/sklambda listeners` and the leak notifier; prefer giving one.",
		"\t",
		"\tBecause the awaited event has already dispatched by the time the body runs (and async events resume on the main thread the next tick), the body cannot cancel or modify the triggering event."
})
@Example("""
		set {_p} to sender
		send "say something in chat within 30 seconds..." to {_p}
		wait for next chat where player is {_p} for at most 30 seconds:
			set {_reply} to message
			on timeout:
				set {_reply} to "<no reply>"
		send "you said: %{_reply}%" to {_p}
		""")
@Since("1.3.0")
public class SecAwait extends EffectSection {

	private static final Pattern TIMEOUT_PATTERN =
			Pattern.compile("\\s+(?:within|for at most|for up to)\\s+(.+?)\\s*$");
	private static final Pattern WHERE_PATTERN = Pattern.compile("\\s+where\\s+(.+?)\\s*$");

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(SecAwait.class)
				.supplier(SecAwait::new)
				.addPatterns("wait for next <.+>", "await [the] next <.+>")
				.build());
	}

	private SkriptEvent skriptEvent;
	private Class<? extends Event>[] eventClasses;
	private String sourceLocation = "unknown";
	private String eventLabel = "";
	private final List<Condition> filters = new ArrayList<>();
	private @Nullable Expression<? extends Timespan> timeoutExpr;
	private @Nullable Trigger receivedTrigger;
	private @Nullable Trigger timeoutTrigger;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		if (!hasSection() || sectionNode == null) {
			Skript.error("`wait for next` requires a body: the code to run once the event arrives.");
			return false;
		}

		String full = parseResult.regexes.get(0).group();
		String timeoutText = null;
		Matcher tm = TIMEOUT_PATTERN.matcher(full);
		if (tm.find()) {
			timeoutText = tm.group(1).trim();
			full = full.substring(0, tm.start());
		}
		String whereText = null;
		Matcher wm = WHERE_PATTERN.matcher(full);
		if (wm.find()) {
			whereText = wm.group(1).trim();
			full = full.substring(0, wm.start());
		}
		String eventPattern = full.trim();
		eventLabel = eventPattern;
		sourceLocation = SectionSupport.sourceLocation(sectionNode);

		skriptEvent = SkriptEvent.parse(eventPattern, sectionNode, "Unrecognized event pattern: " + eventPattern);
		if (skriptEvent == null) return false;
		eventClasses = skriptEvent.getEventClasses();
		if (eventClasses.length == 0) {
			Skript.error("`wait for next` needs a concrete event to await: " + eventPattern);
			return false;
		}

		if (timeoutText != null) {
			timeoutExpr = SectionSupport.parseTimespan(timeoutText);
			if (timeoutExpr == null) return false;
		}

		ParserInstance parser = getParser();
		Class<? extends Event>[] outerEvents = parser.getCurrentEvents();
		String prevEventName = parser.getCurrentEventName();

		if (whereText != null) {
			parser.setCurrentEvent("await filter", eventClasses);
			Condition inlineFilter;
			try {
				inlineFilter = Condition.parse(whereText, "Invalid `where` condition: " + whereText);
			} finally {
				if (outerEvents != null) {
					parser.setCurrentEvent(prevEventName, outerEvents);
				} else {
					parser.deleteCurrentEvent();
				}
			}
			if (inlineFilter == null) return false;
			filters.add(inlineFilter);
		}

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
			Skript.error("`on timeout` requires a timeout, e.g. `... for at most 30 seconds:`.");
			return false;
		}
		if (timeoutNode != null) timeoutNode.remove();

		receivedTrigger = loadCode(sectionNode, "await", eventClasses);
		if (receivedTrigger == null) return false;
		if (timeoutNode != null) {
			Class<? extends Event>[] timeoutEvents = (outerEvents != null && outerEvents.length > 0)
					? outerEvents : eventClasses;
			timeoutTrigger = loadCode(timeoutNode, "await timeout", timeoutEvents);
			if (timeoutTrigger == null) return false;
		}

		// The code after this block runs only once the await resolves, so mark it delayed.
		parser.setHasDelayBefore(Kleenean.TRUE);
		return true;
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		debug(event, true);
		TriggerItem next = getNext();

		long timeoutTicks = -1;
		if (timeoutExpr != null) {
			Timespan ts = timeoutExpr.getSingle(event);
			if (ts != null) timeoutTicks = Math.max(1, ts.getAs(Timespan.TimePeriod.TICK));
		}

		Listener listener = Listener.builder(skriptEvent, eventClasses)
				.filters(filters)
				.onTrigger(receivedTrigger)
				.onTimeout(timeoutTrigger)
				.triggers(1)
				.timeoutTicks(timeoutTicks)
				.sourceLocation(sourceLocation)
				.eventLabel(eventLabel)
				.onResolved(snapshot -> resume(next, event, snapshot))
				.build();
		listener.captureFrom(event);
		listener.register();
		// Suspend: the listener's continuation resumes the trigger when the event arrives or times out.
		return null;
	}

	/** Resumes the awaiting trigger on the next tick, replaying the locals the body produced. */
	private static void resume(@Nullable TriggerItem next, @NotNull Event event, @Nullable Object snapshot) {
		Plugin plugin = SkLambda.getInstance();
		if (plugin == null) return;
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (snapshot != null) Variables.setLocalVariables(event, snapshot);
			Delay.addDelayedEvent(event);
			if (next != null) TriggerItem.walk(next, event);
			Variables.removeLocals(event);
		});
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "wait for next " + skriptEvent.toString(event, debug);
	}

}
