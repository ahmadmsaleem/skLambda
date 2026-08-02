package com.sklambda.elements.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.List;

/**
 * Catches an `on timeout:` / `on failure:` block written at the wrong indentation. Every section that
 * supports one claims it as a child node before the body is parsed, so reaching the generic parser at all
 * means no owning section took it. Undocumented on purpose: it exists only to turn a confusing
 * "can't understand this section" into an actionable message.
 */
public class SecStrayBlock extends Section {

	private static final int FAILURE = 1;

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(SecStrayBlock.class)
				.supplier(SecStrayBlock::new)
				.addPatterns("on timeout", "on failure")
				.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		boolean failure = matchedPattern == FAILURE;
		String keyword = failure ? "on failure" : "on timeout";
		String supportedBy = failure
				? "Only `wait for` (futures) has an `on failure:` block."
				: "It is supported by `listen`, `watch`, `watch when`, `wait for next`, and `wait for`, "
						+ "and each of those needs a timeout clause for it to be reachable.";
		Skript.error("`" + keyword + ":` must be indented inside the section it belongs to, alongside that "
				+ "section's body, not at the same level as it. For example:"
				+ "\n\twait for {_f} for at most 5 seconds:"
				+ "\n\t\tsend \"got it\""
				+ "\n\t\t" + keyword + ":"
				+ "\n\t\t\tsend \"did not get it\""
				+ "\n" + supportedBy);
		return false;
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		return getNext(); // unreachable: init always fails.
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "stray on-block";
	}

}
