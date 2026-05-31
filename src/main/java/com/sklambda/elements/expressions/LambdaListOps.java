package com.sklambda.elements.expressions;

import com.sklambda.elements.types.Lambda;

/** Shared helpers for the lambda-driven list expressions (map / filter / reduce / sort / count / first). */
final class LambdaListOps {

	private LambdaListOps() {}

	/**
	 * Whether every predicate in {@code predicates} passes for {@code element} (all-of semantics, matching the
	 * bare `passes` condition). An empty predicate list never passes; a value that isn't a predicate counts as
	 * not passing. The element is supplied to each predicate as its single argument.
	 */
	static boolean allPass(Object[] predicates, Object element) {
		if (predicates.length == 0) return false;
		Object[] args = {element};
		for (Object predicate : predicates) {
			Lambda lambda = Lambda.from(predicate);
			if (lambda == null || !Boolean.TRUE.equals(lambda.invoke(args))) return false;
		}
		return true;
	}

}
