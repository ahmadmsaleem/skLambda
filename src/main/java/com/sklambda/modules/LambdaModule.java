package com.sklambda.modules;

import com.sklambda.elements.conditions.CondFutureState;
import com.sklambda.elements.conditions.CondPredicatePasses;
import com.sklambda.elements.effects.EffCompleteFuture;
import com.sklambda.elements.effects.EffFailFuture;
import com.sklambda.elements.effects.EffRunLambda;
import com.sklambda.elements.expressions.ExprBoundLambda;
import com.sklambda.elements.expressions.ExprCallLambda;
import com.sklambda.elements.expressions.ExprFunctionLambda;
import com.sklambda.elements.expressions.ExprFutureOfLambda;
import com.sklambda.elements.expressions.ExprFutureResult;
import com.sklambda.elements.expressions.ExprLambda;
import com.sklambda.elements.expressions.ExprMapped;
import com.sklambda.elements.expressions.ExprMinMaxBy;
import com.sklambda.elements.expressions.ExprNewFuture;
import com.sklambda.elements.expressions.ExprPageCount;
import com.sklambda.elements.expressions.ExprPaged;
import com.sklambda.elements.expressions.ExprPiped;
import com.sklambda.elements.expressions.ExprNegatedLambda;
import com.sklambda.elements.expressions.ExprFutureError;
import com.sklambda.elements.expressions.ExprFutureMapped;
import com.sklambda.elements.expressions.ExprReduced;
import com.sklambda.elements.expressions.ExprSettledFuture;
import com.sklambda.elements.expressions.ExprScanned;
import com.sklambda.elements.expressions.ExprSorted;
import com.sklambda.elements.expressions.ExprTakenWhile;
import com.sklambda.elements.expressions.ExprZipped;
import com.sklambda.elements.functions.ConstantPredicateFunctions;
import com.sklambda.elements.sections.SecAwaitFuture;
import com.sklambda.elements.sections.SecLambdaDefine;
import com.sklambda.elements.types.Future;
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
		Future.registerType();
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
		ExprReduced.register(registry);
		ExprScanned.register(registry);
		ExprSorted.register(registry);
		ExprZipped.register(registry);
		ExprMinMaxBy.register(registry);
		ExprPiped.register(registry);
		ExprPaged.register(registry);
		ExprPageCount.register(registry);
		ExprBoundLambda.register(registry);
		ExprNegatedLambda.register(registry);
		ExprTakenWhile.register(registry);
		ConstantPredicateFunctions.register(addon);
		ExprNewFuture.register(registry);
		ExprSettledFuture.register(registry);
		ExprFutureOfLambda.register(registry);
		ExprFutureResult.register(registry);
		ExprFutureError.register(registry);
		ExprFutureMapped.register(registry);
		EffCompleteFuture.register(registry);
		EffFailFuture.register(registry);
		CondFutureState.register(registry);
		SecAwaitFuture.register(registry);
	}

}
