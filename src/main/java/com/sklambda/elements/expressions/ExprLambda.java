package com.sklambda.elements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import com.sklambda.elements.events.LambdaInvocationEvent;
import com.sklambda.elements.types.Lambda;
import com.sklambda.elements.types.LambdaSignature;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Inline Lambda")
@Description({
		"Creates a lambda on a single line, usable anywhere an expression is. The body after `:` is one of:",
		"\t- a condition — the lambda returns whether it holds (a predicate, see `passes`),",
		"\t- an effect — the lambda runs it and returns nothing, or",
		"\t- a value — `return <expression>` (or just a bare expression) makes the lambda return that value.",
		"",
		"Parameters become locals (`{_p}`) inside the body. The lambda also closes over the local "
				+ "variables in scope where it is written — a snapshot taken when the expression is evaluated, "
				+ "readable inside the body when the lambda is later called (parameters shadow captured locals "
				+ "of the same name). For multi-line bodies, use the `set %object% to lambda ...:` section form instead."
})
@Example("""
		set {is-op} to lambda (p: player): {_p} is op
		add lambda (n: number): {_n} > 0 to {positive-checks::*}
		run lambda (p: player): send "hi" with player
		set {_add} to lambda (a: number, b: number): return {_a} + {_b}
		set {_double} to lambda (n: number): {_n} * 2
		""")
@Since("0.0.3-alpha")
public class ExprLambda extends SimpleExpression<Lambda> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprLambda.class, Lambda.class)
				.supplier(ExprLambda::new)
				.addPatterns("lambda[ ]<.+>")
				.build());
	}

	private @Nullable Lambda lambda;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		String raw = parseResult.regexes.get(0).group().trim();
		int colon = topLevelColon(raw);
		if (colon < 0) {
			Skript.error("An inline lambda needs a body after ':', e.g. lambda (p: player): {_p} is op");
			return false;
		}
		String sig = raw.substring(0, colon).trim();
		String bodyText = raw.substring(colon + 1).trim();
		if (bodyText.isEmpty()) {
			Skript.error("Inline lambda body is empty.");
			return false;
		}

		LambdaSignature.Result signature = LambdaSignature.parse(sig);
		if (signature == null) return false;

		// Parse the body against the invocation event so params and locals resolve.
		ParserInstance parser = getParser();
		Class<? extends Event>[] outerEvents = parser.getCurrentEvents();
		String prevEventName = parser.getCurrentEventName();
		parser.setCurrentEvent("lambda", LambdaInvocationEvent.class);
		try {
			Lambda.Body body;
			ClassInfo<?> returnType = signature.returnType();

			// An explicit `return <expression>` is a value lambda — evaluate it and hand the value back.
			String returnExpr = stripLeadingReturn(bodyText);
			if (returnExpr != null) {
				Expression<?> value = new SkriptParser(returnExpr, SkriptParser.ALL_FLAGS).parseExpression(Object.class);
				if (value == null) {
					Skript.error("Can't understand the return value of this inline lambda: " + returnExpr);
					return false;
				}
				body = valueBody(value);
				if (returnType == null) returnType = Classes.getSuperClassInfo(value.getReturnType());
			} else {
				Condition condition = tryParseCondition(bodyText);
				if (condition != null) {
					body = invocation -> condition.check(invocation);
					if (returnType == null) returnType = Classes.getExactClassInfo(Boolean.class);
				} else {
					Effect effect = tryParseEffect(bodyText);
					if (effect != null) {
						body = invocation -> {
							TriggerItem.walk(effect, invocation);
							Variables.removeLocals(invocation);
							return null;
						};
					} else {
						// Last resort: a bare expression body behaves like `return <expression>`.
						Expression<?> value = new SkriptParser(bodyText, SkriptParser.ALL_FLAGS).parseExpression(Object.class);
						if (value == null) {
							Skript.error("Can't understand this inline lambda body: " + bodyText
									+ " — expected a condition, an effect, or a (return) value expression.");
							return false;
						}
						body = valueBody(value);
						if (returnType == null) returnType = Classes.getSuperClassInfo(value.getReturnType());
					}
				}
			}
			lambda = new Lambda(signature.params(), returnType, body);
		} finally {
			if (outerEvents != null) {
				parser.setCurrentEvent(prevEventName, outerEvents);
			} else {
				parser.deleteCurrentEvent();
			}
		}
		return true;
	}

	/** Index of the first `:` not enclosed in parentheses, or -1 if none. */
	private static int topLevelColon(String s) {
		int depth = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '(') depth++;
			else if (c == ')') depth = Math.max(0, depth - 1);
			else if (c == ':' && depth == 0) return i;
		}
		return -1;
	}

	/** Attempts to parse {@code text} as a condition, discarding errors if it isn't one. */
	private static @Nullable Condition tryParseCondition(String text) {
		RetainingLogHandler log = SkriptLogger.startRetainingLog();
		try {
			return Condition.parse(text, null);
		} finally {
			log.stop();
		}
	}

	/** Attempts to parse {@code text} as an effect, discarding errors if it isn't one. */
	private static @Nullable Effect tryParseEffect(String text) {
		RetainingLogHandler log = SkriptLogger.startRetainingLog();
		try {
			return Effect.parse(text, null);
		} finally {
			log.stop();
		}
	}

	/** A lambda body that evaluates {@code value} and returns it, cleaning up the invocation's locals. */
	private static Lambda.Body valueBody(Expression<?> value) {
		return invocation -> {
			Object result = value.getSingle(invocation);
			Variables.removeLocals(invocation);
			return result;
		};
	}

	/** If {@code text} begins with the {@code return} keyword, returns the trimmed remainder; otherwise null. */
	private static @Nullable String stripLeadingReturn(String text) {
		if (text.regionMatches(true, 0, "return", 0, 6)
				&& text.length() > 6 && Character.isWhitespace(text.charAt(6))) {
			String rest = text.substring(6).trim();
			return rest.isEmpty() ? null : rest;
		}
		return null;
	}

	@Override
	protected Lambda @Nullable [] get(@NotNull Event event) {
		if (lambda == null) return new Lambda[0];
		// Capture the surrounding scope at evaluation time: the parsed lambda instance is shared
		// across calls, so each evaluation closes over its own snapshot of the current locals.
		return new Lambda[]{lambda.capturing(Variables.copyLocalVariables(event))};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public @NotNull Class<? extends Lambda> getReturnType() {
		return Lambda.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return lambda != null ? lambda.toString() : "inline lambda";
	}

}
