package com.sklambda.elements.expressions;

import com.sklambda.elements.types.Lambda;

final class LambdaListOps {

	private LambdaListOps() {}

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
