package com.sklambda.modules;

import com.sklambda.elements.conditions.CondPredicatePasses;
import com.sklambda.elements.effects.EffRunLambda;
import com.sklambda.elements.expressions.ExprCallLambda;
import com.sklambda.elements.expressions.ExprCountWhere;
import com.sklambda.elements.expressions.ExprFiltered;
import com.sklambda.elements.expressions.ExprFirstWhere;
import com.sklambda.elements.expressions.ExprFunctionLambda;
import com.sklambda.elements.expressions.ExprLambda;
import com.sklambda.elements.expressions.ExprMapped;
import com.sklambda.elements.expressions.ExprReduced;
import com.sklambda.elements.expressions.ExprSorted;
import com.sklambda.elements.functions.ConstantPredicateFunctions;
import com.sklambda.elements.sections.SecLambdaDefine;
import com.sklambda.elements.types.Lambda;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;


public final class LambdaModule implements AddonModule {

	@Override
	public @NotNull String name() {
		return "lambda";
	}

	@Override
	public void init(@NotNull SkriptAddon addon) {
		Lambda.register();
	}

	@Override
	public void load(@NotNull SkriptAddon addon) {
		SyntaxRegistry registry = addon.syntaxRegistry();
		SecLambdaDefine.register(registry);
		ExprLambda.register(registry);
		ExprFunctionLambda.register(registry);
		ExprCallLambda.register(registry);
		EffRunLambda.register(registry);
		CondPredicatePasses.register(registry);
		ExprMapped.register(registry);
		ExprFiltered.register(registry);
		ExprReduced.register(registry);
		ExprSorted.register(registry);
		ExprCountWhere.register(registry);
		ExprFirstWhere.register(registry);
		ConstantPredicateFunctions.register(addon);
	}

}
