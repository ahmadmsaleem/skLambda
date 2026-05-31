package com.sklambda.elements.functions;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.registrations.Classes;
import com.sklambda.elements.types.Lambda;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.common.function.DefaultFunction;

import java.util.List;

/**
 * The {@code always()} and {@code never()} functions: ready-made predicate lambdas that ignore their
 * arguments and always / never pass. Usable anywhere a predicate is expected (e.g. {@code passes for},
 * a {@code where %predicate% passes} filter, or a predicate list).
 */
public final class ConstantPredicateFunctions {

	private ConstantPredicateFunctions() {}

	public static void register(@NotNull SkriptAddon addon) {
		ClassInfo<Boolean> bool = Classes.getExactClassInfo(Boolean.class);
		Lambda always = new Lambda(List.of(), bool, invocation -> Boolean.TRUE);
		Lambda never = new Lambda(List.of(), bool, invocation -> Boolean.FALSE);

		Functions.register(DefaultFunction.builder(addon, "always", Lambda.class)
				.description("A predicate lambda that always passes: it ignores its arguments and returns true.")
				.examples("set {_yes} to always()", "if always() passes for player:")
				.since("1.0.0")
				.build(args -> always));

		Functions.register(DefaultFunction.builder(addon, "never", Lambda.class)
				.description("A predicate lambda that never passes: it ignores its arguments and returns false.")
				.examples("set {_no} to never()", "if never() doesn't pass for player:")
				.since("1.0.0")
				.build(args -> never));
	}

}
