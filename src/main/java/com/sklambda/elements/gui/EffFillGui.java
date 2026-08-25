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

@Name("Fill a GUI From a List")
@Description({
		"Fills a skript-gui from a list: one lambda turns each element into the item to show, another "
				+ "handles the click on it. This is the whole body of a data-driven menu in one line.",
		"\tThe renderer is called with the element and returns an item. The handler is called with the "
				+ "element, the player who clicked, and the slot index.",
		"\tSlots are filled from `starting at` (default 0) upwards. Pair it with `page %number% of %objects% "
				+ "by %number%` to page a long list across menus.",
		"\tOnly registers when skript-gui is installed."
})
@Example("""
		set {shop::render} to lambda (entry: object) -> object:
			return 1 of paper named "%{_entry}%"
		set {shop::buy} to lambda (entry: object, p: player):
			send "you bought %{_entry}%" to {_p}

		set {_page::*} to page {_n} of {shop::stock::*} by 45
		create a gui with virtual chest inventory named "Shop" with 6 rows
		fill gui (last created gui) from {_page::*} rendered with {shop::render} handled by {shop::buy}
		""")
@RequiredPlugins("skript-gui")
@Since("1.5.0")
public class EffFillGui extends Effect {

	public static void register(@NotNull SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffFillGui.class)
				.supplier(EffFillGui::new)
				.addPatterns("fill gui %object% from %objects% rendered with %object%"
						+ " [(handled|clicked) (by|with) %-object%] [starting at [slot] %-number%]")
				.build());
	}

	private Expression<?> guiExpr;
	private Expression<?> source;
	private Expression<?> renderer;
	private @Nullable Expression<?> handler;
	private @Nullable Expression<? extends Number> start;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, @NotNull Kleenean isDelayed, @NotNull ParseResult parseResult) {
		guiExpr = exprs[0];
		source = LiteralUtils.defendExpression(exprs[1]);
		renderer = exprs[2];
		handler = exprs[3];
		start = (Expression<? extends Number>) exprs[4];
		if (Lambda.isUnparsed(renderer) || Lambda.isUnparsed(handler)) return false;
		return LiteralUtils.canInitSafely(source);
	}

	@Override
	protected void execute(@NotNull Event event) {
		Object gui = guiExpr.getSingle(event);
		if (!GuiBridge.isGui(gui)) return;
		Lambda render = Lambda.from(renderer.getSingle(event));
		if (render == null) return;
		Lambda onClick = handler != null ? Lambda.from(handler.getSingle(event)) : null;

		Number from = start != null ? start.getSingle(event) : null;
		int slot = from == null ? 0 : from.intValue();
		for (Object element : source.getArray(event)) {
			if (!(render.invoke(new Object[]{element}) instanceof ItemType item)) {
				slot++;
				continue;
			}
			// Each slot closes over its own element, which is what makes one shared handler enough.
			int index = slot;
			Consumer<InventoryClickEvent> consumer = onClick == null ? null
					: click -> onClick.invoke(new Object[]{element, click.getWhoClicked(), (long) index});
			GuiBridge.setItem(gui, index, item, false, consumer);
			slot++;
		}
	}

	@Override
	public @NotNull String toString(@Nullable Event event, boolean debug) {
		return "fill gui " + guiExpr.toString(event, debug) + " from " + source.toString(event, debug)
				+ " rendered with " + renderer.toString(event, debug)
				+ (handler != null ? " handled by " + handler.toString(event, debug) : "");
	}

}
