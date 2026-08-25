package com.sklambda.elements.types;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.LiteralUtils;
import com.sklambda.elements.types.Lambda.Param;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LambdaSignature {

	public record Result(List<Param> params, @Nullable ClassInfo<?> returnType) {}

	private LambdaSignature() {}

	public static @Nullable Result parse(String spec) {
		List<Param> params = new ArrayList<>();
		ClassInfo<?> returnType = null;

		if (spec.startsWith("(")) {
			int close = matchingParen(spec);
			if (close < 0) {
				Skript.error("Unclosed lambda parameter list.");
				return null;
			}
			String inside = spec.substring(1, close).trim();
			if (!inside.isEmpty()) {
				for (String piece : splitTopLevel(inside)) {
					int colon = topLevelIndexOf(piece, ':');
					String[] kv = colon < 0 ? new String[]{piece} : new String[]{piece.substring(0, colon), piece.substring(colon + 1)};
					if (kv.length != 2) {
						Skript.error("Lambda parameter must look like name: type (got " + piece.trim() + ").");
						return null;
					}
					String name = kv[0].trim();
					String rest = kv[1].trim();
					String typeName = rest;
					String defaultText = null;
					int eq = topLevelIndexOf(rest, '=');
					if (eq >= 0) {
						typeName = rest.substring(0, eq).trim();
						defaultText = rest.substring(eq + 1).trim();
					}
					ClassInfo<?> info = Classes.getClassInfoFromUserInput(typeName);
					if (info == null) {
						Skript.error("Unknown lambda parameter type: " + typeName);
						return null;
					}
					Expression<?> defaultValue = null;
					if (defaultText != null) {
						if (defaultText.isEmpty()) {
							Skript.error("Lambda parameter '" + name + "' has '=' but no default value.");
							return null;
						}
						defaultValue = new SkriptParser(defaultText, SkriptParser.ALL_FLAGS).parseExpression(Object.class);
						if (defaultValue != null) defaultValue = LiteralUtils.defendExpression(defaultValue);
						if (defaultValue == null || !LiteralUtils.canInitSafely(defaultValue)) {
							Skript.error("Can't understand the default value for lambda parameter '" + name + "': " + defaultText);
							return null;
						}
					}
					params.add(new Param(name, info, defaultValue));
				}
			}
			spec = spec.substring(close + 1).trim();
		}
		if (spec.startsWith("->")) {
			String typeName = spec.substring(2).trim();
			if (!typeName.isEmpty()) {
				ClassInfo<?> info = Classes.getClassInfoFromUserInput(typeName);
				if (info == null) {
					Skript.error("Unknown lambda return type: " + typeName);
					return null;
				}
				returnType = info;
			}
			spec = "";
		}
		if (!spec.isEmpty()) {
			Skript.error("Unexpected text in lambda signature: " + spec);
			return null;
		}
		return new Result(params, returnType);
	}

	/**
	 * The index of the {@code )} closing the {@code (} at position 0, skipping nested brackets and quoted
	 * text. A default value is a full Skript expression, so it may carry its own parentheses and quotes.
	 */
	private static int matchingParen(String spec) {
		int depth = 0;
		boolean quoted = false;
		for (int i = 0; i < spec.length(); i++) {
			char c = spec.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted) {
				if (c == '(' || c == '[' || c == '{') {
					depth++;
				} else if (c == ')' || c == ']' || c == '}') {
					depth--;
					if (depth == 0 && c == ')') return i;
					if (depth < 0) return -1;
				}
			}
		}
		return -1;
	}

	/** Splits a parameter list on the commas that separate parameters, ignoring commas nested in a default value. */
	private static List<String> splitTopLevel(String inside) {
		List<String> pieces = new ArrayList<>();
		int depth = 0;
		boolean quoted = false;
		int start = 0;
		for (int i = 0; i < inside.length(); i++) {
			char c = inside.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted) {
				if (c == '(' || c == '[' || c == '{') {
					depth++;
				} else if (c == ')' || c == ']' || c == '}') {
					depth--;
				} else if (c == ',' && depth == 0) {
					pieces.add(inside.substring(start, i));
					start = i + 1;
				}
			}
		}
		pieces.add(inside.substring(start));
		return pieces;
	}

	/** The index of the first {@code needle} that is not inside brackets or quotes, or -1. */
	private static int topLevelIndexOf(String text, char needle) {
		int depth = 0;
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted) {
				if (c == '(' || c == '[' || c == '{') {
					depth++;
				} else if (c == ')' || c == ']' || c == '}') {
					depth--;
				} else if (c == needle && depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

}
