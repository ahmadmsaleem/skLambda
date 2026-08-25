package com.sklambda.elements.gui;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.LiteralUtils;
import ch.njol.util.Kleenean;
import com.sklambda.elements.types.Lambda;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.function.Consumer;

@Name("Format GUI Slot With a Lambda")
@Description({
		"Puts an item in a skript-gui slot and hands the click to a lambda, so one stored handler can be "
				+ "reused across every GUI instead of being written out again as a section per slot.",
		"\tThe handler is called with three arguments: the player who clicked, the slot index, and the "
				+ "item that was clicked. Declare only the ones you want; a shorter lambda ignores the rest.",
		"\tOnly registers when skript-gui is installed."
})
@Example("""
		set {menu::close} to lambda (p: player):
			close {_p}'s inventory

		create a gui with virtual chest inventory named "Menu" with 3 rows
		format gui slot 26 of (last created gui) with barrier named "Close" handled by {menu::close}
		""")
@RequiredPlugins("skript-gui")
@Since("1.5.0")
public class EffFormatGuiSlot extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffFormatGuiSlot.class)
				.supplier(EffFormatGuiSlot::new)
				.addPatterns(
						"format gui slot[s] %objects% of %object% with [stealable:([re]mov[e]able|stealable)] %itemtype%"
								+ " [(handled|clicked) (by|with) %-object%]",
						"unformat gui slot[s] %objects% of %object%")
				.build());
	}

	private Expression<?> slots;
	private Expression<?> guiExpr;
	private @Nullable Expression<ItemType> item;
	private @Nullable Expression<?> handler;
	private boolean removable;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		slots = LiteralUtils.defendExpression(exprs[0]);
		guiExpr = exprs[1];
		if (matchedPattern == 0) {
			item = (Expression<ItemType>) exprs[2];
			handler = exprs[3];
			if (Lambda.isUnparsed(handler)) return false;
			removable = parseResult.hasTag("stealable");
		}
		return LiteralUtils.canInitSafely(slots);
	}

	@Override
	protected void execute(@NotNull Event event) {
		Object gui = guiExpr.getSingle(event);
		if (!GuiBridge.isGui(gui)) return;
		ItemType type = item != null ? item.getSingle(event) : null;
		Consumer<InventoryClickEvent> onClick = clickConsumer(event);
		for (Object slot : slots.getArray(event)) {
			GuiBridge.setItem(gui, normalise(slot), type, removable, onClick);
		}
	}

	/** The lambda, wrapped so it is called with the values a script actually wants rather than the raw event. */
	private @Nullable Consumer<InventoryClickEvent> clickConsumer(Event event) {
		if (handler == null) return null;
		Lambda lambda = Lambda.from(handler.getSingle(event));
		if (lambda == null) return null;
		return click -> lambda.invoke(new Object[]{click.getWhoClicked(), (long) click.getSlot(), click.getCurrentItem()});
	}

	/** skript-gui keys slots by Integer or by a layout character; Skript hands us longs, so narrow them. */
	private static Object normalise(Object slot) {
		return slot instanceof Number number ? number.intValue() : slot;
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return (item == null ? "unformat" : "format") + " gui slot " + slots.toString(event, debug)
				+ " of " + guiExpr.toString(event, debug)
				+ (item != null ? " with " + item.toString(event, debug) : "")
				+ (handler != null ? " handled by " + handler.toString(event, debug) : "");
	}

}
