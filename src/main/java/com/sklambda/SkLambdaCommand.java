package com.sklambda;

import com.sklambda.elements.types.Listener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Handles {@code /sklambda}: shows the version/link, or lists active listeners with {@code /sklambda listeners}. */
public final class SkLambdaCommand implements CommandExecutor {

	private static final String github = "https://github.com/ahmadmsaleem/skLambda";

	private final SkLambda plugin;

	public SkLambdaCommand(SkLambda plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("listeners")) {
			sendListeners(sender);
			return true;
		}

		sender.sendMessage(Component.text("skLambda ", NamedTextColor.RED)
				.append(Component.text("v" + plugin.getPluginMeta().getVersion(), NamedTextColor.WHITE)));

		Component link = Component.text(github, NamedTextColor.WHITE)
				.decorate(TextDecoration.UNDERLINED)
				.clickEvent(ClickEvent.openUrl(github))
				.hoverEvent(HoverEvent.showText(Component.text("Click to open in your browser", NamedTextColor.WHITE)));
		sender.sendMessage(Component.text("» GitHub: ", NamedTextColor.RED).append(link));
		return true;
	}

	private void sendListeners(@NotNull CommandSender sender) {
		List<Listener> active = Listener.activeListeners();
		sender.sendMessage(Component.text("Active listeners: ", NamedTextColor.RED)
				.append(Component.text(active.size(), NamedTextColor.WHITE)));
		for (Listener listener : active) {
			String label = listener.getEventLabel().isEmpty() ? "listener" : listener.getEventLabel() + " listener";
			sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
					.append(Component.text(listener.getSourceLocation(), NamedTextColor.WHITE))
					.append(Component.text(" " + label + ", alive " + Listener.formatDuration(listener.getAliveMillis()), NamedTextColor.GRAY)));
		}
	}

}
