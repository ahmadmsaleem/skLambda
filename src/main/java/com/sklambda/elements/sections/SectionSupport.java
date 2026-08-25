package com.sklambda.elements.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.util.Timespan;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Shared parsing chores for the listen/watch/await sections: turning entry text into expressions and locating a node. */
final class SectionSupport {

	private SectionSupport() {}

	/** Parses {@code text} as a timespan expression (e.g. an entry value), reporting an error and returning null if it isn't one. */
	@SuppressWarnings("unchecked")
	static @Nullable Expression<? extends Timespan> parseTimespan(String text) {
		Expression<?> e = new SkriptParser(text, SkriptParser.ALL_FLAGS).parseExpression(Timespan.class);
		if (e == null) {
			Skript.error("Expected a timespan (e.g. 30 seconds), got: " + text);
			return null;
		}
		return (Expression<? extends Timespan>) e;
	}

	/** Parses {@code text} as a listener owner (offline player, entity, chunk, world, or inventory), reporting an error and returning null otherwise. */
	static @Nullable Expression<?> parseOwner(String text) {
		Expression<?> e = new SkriptParser(text, SkriptParser.ALL_FLAGS)
				.parseExpression(OfflinePlayer.class, Entity.class, Chunk.class, World.class, Inventory.class);
		if (e == null) {
			Skript.error("Expected an owner (an offline player, entity, chunk, world, or inventory) but got: " + text);
			return null;
		}
		return e;
	}

	/** Parses {@code text} as a boolean expression (e.g. an `initial:` entry), reporting an error and returning null otherwise. */
	@SuppressWarnings("unchecked")
	static @Nullable Expression<? extends Boolean> parseBoolean(String text) {
		Expression<?> e = new SkriptParser(text, SkriptParser.ALL_FLAGS).parseExpression(Boolean.class);
		if (e == null) {
			Skript.error("Expected true or false, got: " + text);
			return null;
		}
		return (Expression<? extends Boolean>) e;
	}

	/** A short "file:line" label for a section node, used in /sklambda listeners and leak warnings. */
	static String sourceLocation(SectionNode node) {
		String fileName = node.getConfig().getFileName();
		int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
		return (slash >= 0 ? fileName.substring(slash + 1) : fileName) + ":" + node.getLine();
	}

	/**
	 * The script's name the way Skript's own `script` expression spells it: relative to the scripts folder,
	 * with the folders kept. {@link #sourceLocation} stays a bare file name for the compact console listing.
	 */
	static String scriptName(SectionNode node) {
		String fileName = node.getConfig().getFileName().replace('\\', '/');
		int scripts = fileName.lastIndexOf("/scripts/");
		return scripts >= 0 ? fileName.substring(scripts + "/scripts/".length()) : fileName;
	}

}
