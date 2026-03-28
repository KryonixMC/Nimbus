package dev.nimbus.sdk;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages player name tags (above head) using Scoreboard Teams.
 * Syncs prefix/suffix from the Nimbus permission system so name tags
 * match the tab list display.
 */
public class NimbusNameTagHandler {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final String apiUrl;
    private final String token;
    private final HttpClient httpClient;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Cache: UUID -> display info from permissions
    private final ConcurrentHashMap<UUID, DisplayInfo> displayCache = new ConcurrentHashMap<>();

    // Per-player overrides from Nimbus.setTabName() (UUID -> MiniMessage format)
    private final ConcurrentHashMap<UUID, String> tabOverrides = new ConcurrentHashMap<>();

    // Track which team each player is on
    private final ConcurrentHashMap<UUID, String> playerTeams = new ConcurrentHashMap<>();

    private static final String TEAM_PREFIX = "nimbus_";

    public NimbusNameTagHandler(JavaPlugin plugin, String apiUrl, String token) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void start() {
        // Listen for permission changes via event stream
        if (Nimbus.events() != null) {
            Nimbus.events().onEvent("PERMISSION_GROUP_UPDATED", e -> {
                // Refresh all online players
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        fetchAndApply(player);
                    }
                });
            });

            Nimbus.events().onEvent("PLAYER_PERMISSIONS_UPDATED", e -> {
                String uuid = e.get("uuid");
                if (uuid != null) {
                    try {
                        UUID playerUuid = UUID.fromString(uuid);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player player = Bukkit.getPlayer(playerUuid);
                            if (player != null) fetchAndApply(player);
                        });
                    } catch (IllegalArgumentException ignored) {}
                }
            });

            // Listen for tab name overrides (from Nimbus.setTabName() in minigames)
            Nimbus.events().onEvent("PLAYER_TAB_UPDATED", e -> {
                String uuid = e.get("uuid");
                String format = e.get("format");
                if (uuid == null) return;
                try {
                    UUID playerUuid = UUID.fromString(uuid);
                    if (format != null && !format.isEmpty()) {
                        tabOverrides.put(playerUuid, format);
                    } else {
                        tabOverrides.remove(playerUuid);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player player = Bukkit.getPlayer(playerUuid);
                        if (player != null) applyNameTag(player);
                    });
                } catch (IllegalArgumentException ignored) {}
            });
        }

        logger.info("Name tag handler started");
    }

    /**
     * Called on player join — fetch display info and apply name tag.
     */
    public void onJoin(Player player) {
        fetchAndApply(player);
    }

    /**
     * Called on player quit — clean up team membership.
     */
    public void onQuit(Player player) {
        displayCache.remove(player.getUniqueId());
        tabOverrides.remove(player.getUniqueId());
        String teamName = playerTeams.remove(player.getUniqueId());
        if (teamName != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                // Clean up empty teams
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }
    }

    private void fetchAndApply(Player player) {
        httpClient.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + "/api/permissions/players/" + player.getUniqueId()))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() >= 400) return;
            try {
                JsonObject json = new com.google.gson.Gson().fromJson(response.body(), JsonObject.class);
                String prefix = json.has("prefix") && !json.get("prefix").isJsonNull() ? json.get("prefix").getAsString() : "";
                String suffix = json.has("suffix") && !json.get("suffix").isJsonNull() ? json.get("suffix").getAsString() : "";
                String group = json.has("displayGroup") && !json.get("displayGroup").isJsonNull() ? json.get("displayGroup").getAsString() : "default";
                int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;

                DisplayInfo info = new DisplayInfo(prefix, suffix, group, priority);
                displayCache.put(player.getUniqueId(), info);

                // Apply on main thread (Bukkit API requirement)
                Bukkit.getScheduler().runTask(plugin, () -> applyNameTag(player));
            } catch (Exception e) {
                logger.fine("Failed to fetch display info for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    private void applyNameTag(Player player) {
        UUID uuid = player.getUniqueId();
        String override = tabOverrides.get(uuid);
        DisplayInfo info = displayCache.getOrDefault(uuid, new DisplayInfo("", "", "default", 0));

        String prefix;
        String suffix;
        int priority;

        if (override != null) {
            // Tab override active (e.g. from Nimbus.setTabName(uuid, "<red>[RED] {player}"))
            // Extract the part before {player} as prefix, after as suffix
            String translated = ColorUtil.translate(override);
            int playerIdx = translated.indexOf("{player}");
            if (playerIdx >= 0) {
                prefix = translated.substring(0, playerIdx);
                suffix = translated.substring(playerIdx + "{player}".length());
            } else {
                // No {player} placeholder — use entire override as prefix
                prefix = translated + " ";
                suffix = "";
            }
            priority = info.priority;
        } else {
            prefix = ColorUtil.translate(info.prefix);
            suffix = ColorUtil.translate(info.suffix);
            priority = info.priority;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Remove from old team
        String oldTeamName = playerTeams.get(uuid);
        if (oldTeamName != null) {
            Team oldTeam = scoreboard.getTeam(oldTeamName);
            if (oldTeam != null) {
                oldTeam.removeEntry(player.getName());
                if (oldTeam.getEntries().isEmpty()) {
                    oldTeam.unregister();
                }
            }
        }

        // Unique team per player when override is active, shared by priority otherwise
        String teamName;
        if (override != null) {
            // Per-player team for overrides (unique name based on player)
            teamName = TEAM_PREFIX + player.getName().substring(0, Math.min(player.getName().length(), 8));
        } else {
            // Shared team by priority (for sorting in tab)
            String sortKey = String.format("%04d", 9999 - Math.max(0, Math.min(9999, priority)));
            teamName = TEAM_PREFIX + sortKey;
        }
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Set prefix/suffix using Adventure components (Paper supports this)
        team.prefix(miniMessage.deserialize(prefix));
        team.suffix(miniMessage.deserialize(suffix));

        team.addEntry(player.getName());
        playerTeams.put(uuid, teamName);
    }

    record DisplayInfo(String prefix, String suffix, String group, int priority) {}
}
