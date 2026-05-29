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
		"Creates a lambda on a single line, usable anywhere an expression is. The body after `:` is either:",
		"\t- a condition — the lambda returns whether it holds (a predicate, see `passes`), or",
		"\t- an effect — the lambda runs it and returns nothing.",
		"",
		"Parameters become locals (`{_p}`) inside the body. For multi-line bodies or explicit "
				+ "`return` values, use the `set %object% to lambda ...:` section form instead."
})
@Example("""
		set {is-op} to lambda (p: player): {_p} is op
		add lambda (n: number): {_n} > 0 to {positive-checks::*}
		run lambda (p: player): send "hi" with player
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
			Condition condition = tryParseCondition(bodyText);
			if (condition != null) {
				body = invocation -> condition.check(invocation);
				if (returnType == null) returnType = Classes.getExactClassInfo(Boolean.class);
			} else {
				Effect effect = Effect.parse(bodyText, "Can't understand this inline lambda body: " + bodyText);
				if (effect == null) return false;
				body = invocation -> {
					TriggerItem.walk(effect, invocation);
					Variables.removeLocals(invocation);
					return null;
				};
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

	@Override
	protected Lambda @Nullable [] get(@NotNull Event event) {
		return lambda == null ? new Lambda[0] : new Lambda[]{lambda};
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
