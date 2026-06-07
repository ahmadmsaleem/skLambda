package com.sklambda.elements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Predicate Passes")
@Description({
		"Treats a lambda as a predicate: invokes it with the given arguments and checks whether it returned true.",
		"",
		"With a list of lambdas, an optional quantifier decides how many must pass:",
		"\t`all of` (the default) passes only if **every** predicate passes. `{checks::*} passes` and"
				+ "`all of {checks::*} passes` mean the same thing.",
		"\t`any of` passes if **at least one** predicate passes (OR semantics).",
		"\t`none of` passes if **no** predicate passes.",
		"\t`at least N of`, `at most N of`, and `exactly N of` pass when the number of passing predicates is "
				+ "at least, at most, or exactly N.",
		"",
		"\tThe negated forms (`doesn't pass`, or a leading `not`) invert the result. A lambda that isn't a "
				+ "predicate (doesn't return true) counts as not passing. An empty list never passes `all of`/`any of` "
				+ "and always passes `none of`.",
		"",
		"\tPredicates run in their own context, so write them to take the value(s) they test as parameters "
				+ "and supply them after `for`."
})
@Example("""
		set {is-op} to lambda (p: player): {_p} is op
		if {is-op} passes for player:
			send "you're staff" to player

		if any of {is-mod::*} passes for {_p}:
			send "mod access" to {_p}

		if none of {is-banned::*} passes for {_p}:
			send "welcome" to {_p}

		if at least 2 of {requirements::*} passes for {_p}:
			send "you qualify" to {_p}

		if not {is-op} passes for {_p}:
			send "not opped" to {_p}

		listen for block break where {is-stone} passes for event-block:
			on trigger: send "stone!" to event-player
		""")
@Since("0.0.3-alpha")
public class CondPredicatePasses extends Condition {

	private static final Object[] NO_ARGS = new Object[0];

	private enum Quantifier { ALL, ANY, NONE, AT_LEAST, AT_MOST, EXACTLY }

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondPredicatePasses.class)
				.supplier(CondPredicatePasses::new)
				.addPatterns(
						"[not:not] [(all:all|any:any|none:none) of] %objects% pass[es] [for %-objects%]",
						"[not:not] [(all:all|any:any|none:none) of] %objects% (match[es]|hold[s]) [for %-objects%]",
						"[(all:all|any:any|none:none) of] %objects% (doesn't|does not|do not|don't) (pass|match|hold) [for %-objects%]",
						"[not:not] (atleast:at least|atmost:at most|exactly:exactly) %number% of %objects% pass[es] [for %-objects%]",
						"[not:not] (atleast:at least|atmost:at most|exactly:exactly) %number% of %objects% (match[es]|hold[s]) [for %-objects%]",
						"(atleast:at least|atmost:at most|exactly:exactly) %number% of %objects% (doesn't|does not|do not|don't) (pass|match|hold) [for %-objects%]")
				.build());
	}

	private Expression<?> predicates;
	private @Nullable Expression<?> argsExpr;
	private @Nullable Expression<?> countExpr;
	private Quantifier quantifier = Quantifier.ALL;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		boolean counting = matchedPattern >= 3;
		int predicateIndex = counting ? 1 : 0;
		int argsIndex = counting ? 2 : 1;
		if (counting) countExpr = exprs[0];
		predicates = LiteralUtils.defendExpression(exprs[predicateIndex]);
		if (!LiteralUtils.canInitSafely(predicates)) return false;
		if (exprs[argsIndex] != null) {
			argsExpr = LiteralUtils.defendExpression(exprs[argsIndex]);
			if (!LiteralUtils.canInitSafely(argsExpr)) return false;
		}
		if (parseResult.hasTag("atleast")) quantifier = Quantifier.AT_LEAST;
		else if (parseResult.hasTag("atmost")) quantifier = Quantifier.AT_MOST;
		else if (parseResult.hasTag("exactly")) quantifier = Quantifier.EXACTLY;
		else if (parseResult.hasTag("any")) quantifier = Quantifier.ANY;
		else if (parseResult.hasTag("none")) quantifier = Quantifier.NONE;
		else quantifier = Quantifier.ALL;
		setNegated(matchedPattern == 2 || matchedPattern == 5 || parseResult.hasTag("not"));
		return true;
	}

	@Override
	public boolean check(@NotNull Event event) {
		Object[] values = predicates.getArray(event);
		Object[] args = argsExpr != null ? argsExpr.getArray(event) : NO_ARGS;
		return evaluate(values, args, event) ^ isNegated();
	}

	private boolean evaluate(Object[] values, Object[] args, Event event) {
		switch (quantifier) {
			case ANY -> {
				for (Object value : values) {
					if (passes(value, args)) return true;
				}
				return false;
			}
			case NONE -> {
				for (Object value : values) {
					if (passes(value, args)) return false;
				}
				return true;
			}
			case AT_LEAST, AT_MOST, EXACTLY -> {
				int n = count(event);
				int passing = countPassing(values, args);
				if (quantifier == Quantifier.AT_LEAST) return passing >= n;
				if (quantifier == Quantifier.AT_MOST) return passing <= n;
				return passing == n;
			}
			default -> {
				if (values.length == 0) return false;
				for (Object value : values) {
					if (!passes(value, args)) return false;
				}
				return true;
			}
		}
	}

	private int count(Event event) {
		Object n = countExpr != null ? countExpr.getSingle(event) : null;
		return n instanceof Number number ? number.intValue() : 0;
	}

	private static int countPassing(Object[] values, Object[] args) {
		int passing = 0;
		for (Object value : values) {
			if (passes(value, args)) passing++;
		}
		return passing;
	}

	private static boolean passes(Object value, Object[] args) {
		Lambda lambda = Lambda.from(value);
		return lambda != null && Boolean.TRUE.equals(lambda.invoke(args));
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		String count = countExpr != null ? countExpr.toString(event, debug) + " of " : "";
		String quant = switch (quantifier) {
			case ANY -> "any of ";
			case NONE -> "none of ";
			case ALL -> "all of ";
			case AT_LEAST -> "at least " + count;
			case AT_MOST -> "at most " + count;
			case EXACTLY -> "exactly " + count;
		};
		return quant + predicates.toString(event, debug)
				+ (isNegated() ? " doesn't pass" : " passes")
				+ (argsExpr != null ? " for " + argsExpr.toString(event, debug) : "");
	}

}
