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
			int close = spec.indexOf(')');
			if (close < 0) {
				Skript.error("Unclosed lambda parameter list.");
				return null;
			}
			String inside = spec.substring(1, close).trim();
			if (!inside.isEmpty()) {
				for (String piece : inside.split(",")) {
					String[] kv = piece.split(":", 2);
					if (kv.length != 2) {
						Skript.error("Lambda parameter must look like name: type (got " + piece.trim() + ").");
						return null;
					}
					String name = kv[0].trim();
					String rest = kv[1].trim();
					String typeName = rest;
					String defaultText = null;
					int eq = rest.indexOf('=');
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

}
