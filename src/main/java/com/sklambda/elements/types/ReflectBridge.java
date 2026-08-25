package com.sklambda.elements.types;

import ch.njol.skript.lang.function.Function;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Adapts skript-reflect's two callable values into lambdas: a stored section and a function reference.
 *
 * <p>skLambda does not build against skript-reflect, so everything is looked up by name and the bridge
 * stays inert when that plugin is absent. {@link Lambda#from} consults it after its own instanceof check,
 * so the fast path for a real lambda is untouched and every syntax that takes a lambda gains support for
 * both at once.
 */
final class ReflectBridge {

	private static final String SECTION = "com.btk5h.skriptmirror.skript.reflect.sections.Section";
	private static final String SECTION_EVENT = "com.btk5h.skriptmirror.skript.reflect.sections.SectionEvent";
	private static final String FUNCTION_REFERENCE = "com.btk5h.skriptmirror.FunctionWrapper";

	private static volatile boolean resolved;
	private static @Nullable Class<?> sectionType;
	private static @Nullable Method sectionRun;
	private static @Nullable Method sectionOutput;
	private static @Nullable Class<?> referenceType;
	private static @Nullable Method referenceFunction;
	private static @Nullable Method referenceArguments;

	private ReflectBridge() {}

	/** The lambda view of a skript-reflect value, or null if it is neither of them. */
	static @Nullable Lambda adapt(@Nullable Object value) {
		if (value == null) return null;
		if (!resolved) resolve();
		if (sectionType != null && sectionType.isInstance(value)) return fromSection(value);
		if (referenceType != null && referenceType.isInstance(value)) return fromReference(value);
		return null;
	}

	private static synchronized void resolve() {
		if (resolved) return;
		try {
			Class<?> found = Class.forName(SECTION);
			sectionRun = found.getMethod("run", Object[][].class);
			// The result is on the event the run returns, not on the section itself.
			sectionOutput = Class.forName(SECTION_EVENT).getMethod("getOutput");
			sectionType = found;
		} catch (ReflectiveOperationException | LinkageError ignored) {
			sectionType = null;
		}
		try {
			Class<?> found = Class.forName(FUNCTION_REFERENCE);
			referenceFunction = found.getMethod("getFunction");
			referenceArguments = found.getMethod("getArguments");
			referenceType = found;
		} catch (ReflectiveOperationException | LinkageError ignored) {
			referenceType = null;
		}
		resolved = true;
	}

	/**
	 * A section becomes a lambda with no declared parameters: it takes its arguments through its own
	 * `with arguments variables` list, so binding them by name here would fight with that.
	 */
	private static Lambda fromSection(Object section) {
		Method run = sectionRun;
		Method output = sectionOutput;
		Lambda.Body body = invocation -> {
			Object[] args = invocation.getArgs();
			Object[][] params = new Object[args.length][];
			for (int i = 0; i < args.length; i++) {
				params[i] = new Object[]{args[i]};
			}
			try {
				Object sectionEvent = run.invoke(section, (Object) params);
				return sectionEvent == null ? null : (Object[]) output.invoke(sectionEvent);
			} catch (ReflectiveOperationException failed) {
				invocation.markErrored();
				return null;
			}
		};
		return new Lambda(List.of(), null, body);
	}

	/** A function reference keeps any arguments it was written with, so those stay bound as leading ones. */
	private static @Nullable Lambda fromReference(Object reference) {
		try {
			if (!(referenceFunction.invoke(reference) instanceof Function<?> function)) return null;
			Lambda lambda = Lambda.fromFunction(function);
			Object[] bound = (Object[]) referenceArguments.invoke(reference);
			return bound == null || bound.length == 0 ? lambda : lambda.bind(bound);
		} catch (ReflectiveOperationException failed) {
			return null;
		}
	}

}
