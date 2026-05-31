package com.sklambda;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;


public final class UpdateChecker implements Listener {

	private static final String RELEASES_API = "https://api.modrinth.com/v2/project/TmcZveKm/version";
	private static final String RELEASES_PAGE = "https://modrinth.com/plugin/sklambda";

	private final SkLambda plugin;
	private final String currentVersion;

	private volatile @Nullable String newerTag;

	public UpdateChecker(SkLambda plugin) {
		this.plugin = plugin;
		this.currentVersion = plugin.getPluginMeta().getVersion();
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		// Don't block startup on a network call.
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::check);
	}

	private void check() {
		String tag = fetchLatestTag();
		if (tag == null || tag.equalsIgnoreCase(currentVersion)) return;
		newerTag = tag;
		plugin.getLogger().warning("A new version of skLambda is available: " + tag
				+ " (you are running " + currentVersion + "). Download: " + RELEASES_PAGE);
	}

	private @Nullable String fetchLatestTag() {
		try {
			HttpURLConnection connection = (HttpURLConnection) URI.create(RELEASES_API).toURL().openConnection();
			connection.setRequestProperty("User-Agent", "skLambda");
			connection.setRequestProperty("Accept", "application/json");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
				JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
				if (versions.isEmpty()) return null;
				JsonObject latest = versions.get(0).getAsJsonObject();
				if (!latest.has("version_number") || latest.get("version_number").isJsonNull()) return null;
				return latest.get("version_number").getAsString().trim();
			}
		} catch (Exception e) {
			plugin.getLogger().warning("Could not check for skLambda updates: " + e.getMessage());
			return null;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		String tag = newerTag;
		if (tag == null) return; // up to date, or the check hasn't finished yet
		Player player = event.getPlayer();
		if (!player.isOp()) return;

		player.sendMessage(Component.text("[skLambda] ", NamedTextColor.RED)
				.append(Component.text("A new version is available: ", NamedTextColor.WHITE))
				.append(Component.text(tag, NamedTextColor.RED))
				.append(Component.text(" — ", NamedTextColor.WHITE))
				.append(Component.text("download here", NamedTextColor.GREEN)
						.decorate(TextDecoration.UNDERLINED)
						.clickEvent(ClickEvent.openUrl(RELEASES_PAGE))
						.hoverEvent(HoverEvent.showText(Component.text("Open the releases page", NamedTextColor.WHITE)))));
	}

}
