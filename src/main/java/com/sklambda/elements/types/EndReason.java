package com.sklambda.elements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Why a {@link Listener} stopped, exposed to scripts inside an {@code on end} callback. */
public enum EndReason {

	COMPLETION("completion"),
	TIMEOUT("timeout"),
	CANCELLED("cancelled"),
	UNREGISTERED("unregistered");

	private final String displayName;

	EndReason(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	/** Parses a user-typed reason (e.g. {@code timeout}), accepting a few synonyms, or null if unknown. */
	public static @Nullable EndReason parse(String input) {
		String s = input.trim().toLowerCase(Locale.ROOT);
		switch (s) {
			case "completion", "complete", "completed" -> { return COMPLETION; }
			case "timeout", "timed out", "time out", "timed-out" -> { return TIMEOUT; }
			case "cancelled", "canceled", "cancel" -> { return CANCELLED; }
			case "unregistered", "unregister", "unregistration" -> { return UNREGISTERED; }
			default -> { return null; }
		}
	}

	public static void register() {
		Classes.registerClass(new ClassInfo<>(EndReason.class, "endreason")
				.user("end ?reasons?")
				.name("Listener End Reason")
				.description("Why a listener stopped, available inside `on end`: completion, timeout, cancelled, or unregistered.")
				.usage("completion, timeout, cancelled, unregistered")
				.since("1.0.0")
				.parser(new Parser<>() {
					@Override
					public @Nullable EndReason parse(@NotNull String input, @NotNull ParseContext context) {
						return EndReason.parse(input);
					}

					@Override
					public boolean canParse(@NotNull ParseContext context) {
						return true;
					}

					@Override
					public @NotNull String toString(EndReason reason, int flags) {
						return reason.getDisplayName();
					}

					@Override
					public @NotNull String toVariableNameString(EndReason reason) {
						return reason.getDisplayName();
					}
				}));
	}

}
