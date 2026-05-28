package com.sklambda.elements.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.EffectSection;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ReturnHandler;
import ch.njol.skript.lang.ReturnableTrigger;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.registrations.Classes;
import ch.njol.util.Kleenean;
import com.sklambda.elements.events.LambdaInvocationEvent;
import com.sklambda.elements.types.Lambda;
import com.sklambda.elements.types.Lambda.Param;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Lambda Definition")
@Description("Defines a lambda and assigns it to a variable. The body uses `return X` to produce a value.")
@Example("""
		set {_double} to lambda (n: number) -> number:
			return {_n} * 2

		set {_greet} to lambda (p: player):
			send "Hello %{_p}%!" to {_p}

		set {_now_ms} to lambda -> number:
			return unix timestamp of now
		""")
@Since("0.0.1-alpha")
public class SecLambdaDefine extends EffectSection implements ReturnHandler<Object> {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(SecLambdaDefine.class)
				.supplier(SecLambdaDefine::new)
				.addPatterns("set %~object% to lambda[ <.+>]")
				.build());
	}

	private Expression<?> target;
	private List<Param> params;
	private @Nullable ClassInfo<?> returnType;
	private ReturnableTrigger<Object> trigger;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed,
						@NotNull ParseResult parseResult, @Nullable SectionNode sectionNode,
						@Nullable List<TriggerItem> triggerItems) {
		if (!hasSection() || sectionNode == null) {
			Skript.error("Lambda definitions need a body, write set X to lambda ...: with indented code below.");
			return false;
		}
		target = exprs[0];
		if (!(target instanceof Variable<?>)) {
			Skript.error("Lambda target must be a variable.");
			return false;
		}

		params = new ArrayList<>();
		returnType = null;
		String spec = parseResult.regexes.isEmpty() ? "" : parseResult.regexes.get(0).group().trim();
		if (!parseSpec(spec)) return false;

		Class<? extends Event>[] events = (Class<? extends Event>[]) new Class<?>[]{LambdaInvocationEvent.class};
		trigger = loadReturnableSectionCode(sectionNode, "lambda", events);
		return true;
	}

	private boolean parseSpec(String spec) {
		if (spec.startsWith("(")) {
			int close = spec.indexOf(')');
			if (close < 0) {
				Skript.error("Unclosed lambda parameter list.");
				return false;
			}
			String inside = spec.substring(1, close).trim();
			if (!inside.isEmpty()) {
				for (String piece : inside.split(",")) {
					String[] kv = piece.split(":", 2);
					if (kv.length != 2) {
						Skript.error("Lambda parameter must look like `name: type` (got `" + piece.trim() + "`).");
						return false;
					}
					String name = kv[0].trim();
					String typeName = kv[1].trim();
					ClassInfo<?> info = Classes.getClassInfoFromUserInput(typeName);
					if (info == null) {
						Skript.error("Unknown lambda parameter type: " + typeName);
						return false;
					}
					params.add(new Param(name, info));
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
					return false;
				}
				returnType = info;
			}
			spec = "";
		}
		if (!spec.isEmpty()) {
			Skript.error("Unexpected text in lambda signature: " + spec);
			return false;
		}
		return true;
	}

	@Override
	protected @Nullable TriggerItem walk(@NotNull Event event) {
		Lambda lambda = new Lambda(params, returnType, trigger);
		target.change(event, new Object[]{lambda}, ChangeMode.SET);
		return walk(event, false);
	}

	@Override
	public void returnValues(@NotNull Event event, @NotNull Expression<?> value) {
		if (event instanceof LambdaInvocationEvent lambdaEvent) {
			lambdaEvent.setReturnValue(value.getSingle(event));
		}
	}

	@Override
	public boolean isSingleReturnValue() {
		return true;
	}

	@Override
	public @Nullable Class<?> returnValueType() {
		return returnType != null ? returnType.getC() : Object.class;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "set " + target.toString(event, debug) + " to lambda";
	}

}
