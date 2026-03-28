package dev.nimbus.sdk;

import com.google.gson.JsonObject;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles chat formatting on Paper backend servers using the proxy sync config
 * and permission group prefix/suffix from Nimbus Core.
 */
public class NimbusChatRenderer implements Listener, ChatRenderer {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final String apiUrl;
    private final String token;
    private final HttpClient httpClient;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private volatile String chatFormat = "{prefix}{player}{suffix} <dark_gray>» <gray>{message}";
    private volatile boolean chatEnabled = true;

    // Per-player display info cache (UUID -> prefix/suffix)
    private final ConcurrentHashMap<UUID, DisplayInfo> displayCache = new ConcurrentHashMap<>();

    public NimbusChatRenderer(JavaPlugin plugin, String apiUrl, String token) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void start() {
        // Fetch initial config
        fetchChatConfig();

        // Register chat event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Listen for config changes via event stream
        if (Nimbus.events() != null) {
            Nimbus.events().onEvent("CHAT_FORMAT_UPDATED", e -> {
                String fmt = e.get("format");
                if (fmt != null) chatFormat = fmt;
                String en = e.get("enabled");
                if (en != null) chatEnabled = Boolean.parseBoolean(en);
                logger.fine("Chat format updated via event");
            });

            Nimbus.events().onEvent("PERMISSION_GROUP_UPDATED", e -> {
                // Refresh all cached display info
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    fetchPlayerDisplay(player.getUniqueId());
                }
            });

            Nimbus.events().onEvent("PLAYER_PERMISSIONS_UPDATED", e -> {
                String uuid = e.get("uuid");
                if (uuid != null) {
                    try {
                        fetchPlayerDisplay(UUID.fromString(uuid));
                    } catch (IllegalArgumentException ignored) {}
                }
            });
        }

        logger.info("Chat renderer started");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!chatEnabled) return;
        event.renderer(this);
    }

    @Override
    public @NotNull Component render(@NotNull Player source, @NotNull Component sourceDisplayName,
                                      @NotNull Component message, @NotNull Audience viewer) {
        DisplayInfo display = displayCache.getOrDefault(source.getUniqueId(), DisplayInfo.EMPTY);

        // Serialize the message to plain text so MiniMessage can handle colors across the entire string
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);

        String formatted = chatFormat
                .replace("{prefix}", display.prefix)
                .replace("{suffix}", display.suffix)
                .replace("{player}", source.getName())
                .replace("{message}", plainMessage)
                .replace("{server}", Nimbus.isManaged() ? Nimbus.name() : "")
                .replace("{group}", Nimbus.isManaged() ? Nimbus.group() : "");

        return miniMessage.deserialize(ColorUtil.translate(formatted));
    }

    /**
     * Fetch and cache display info for a player. Called on join and on permission changes.
     */
    public void fetchPlayerDisplay(UUID uuid) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/api/permissions/players/" + uuid))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                if (response.statusCode() >= 400) return;
                try {
                    JsonObject json = new com.google.gson.Gson().fromJson(response.body(), JsonObject.class);
                    String prefix = json.has("prefix") && !json.get("prefix").isJsonNull() ? json.get("prefix").getAsString() : "";
                    String suffix = json.has("suffix") && !json.get("suffix").isJsonNull() ? json.get("suffix").getAsString() : "";
                    displayCache.put(uuid, new DisplayInfo(prefix, suffix));
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            logger.fine("Failed to fetch display info for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Remove cached display info on disconnect.
     */
    public void removePlayer(UUID uuid) {
        displayCache.remove(uuid);
    }

    private void fetchChatConfig() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/api/proxy/chat"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                logger.warning("Failed to fetch chat config: HTTP " + response.statusCode());
                return;
            }

            JsonObject json = new com.google.gson.Gson().fromJson(response.body(), JsonObject.class);
            if (json.has("format")) chatFormat = json.get("format").getAsString();
            if (json.has("enabled")) chatEnabled = json.get("enabled").getAsBoolean();
            logger.info("Loaded chat format from API");
        } catch (Exception e) {
            logger.warning("Failed to fetch chat config: " + e.getMessage());
        }
    }

    record DisplayInfo(String prefix, String suffix) {
        static final DisplayInfo EMPTY = new DisplayInfo("", "");
    }
}
