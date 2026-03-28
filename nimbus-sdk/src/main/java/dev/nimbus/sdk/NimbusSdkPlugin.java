package dev.nimbus.sdk;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Nimbus SDK Paper plugin.
 * <p>
 * Automatically initializes {@link Nimbus} on startup if running in a
 * Nimbus-managed service. Other plugins just call {@code Nimbus.setState("WAITING")}
 * etc. — no setup required.
 */
public class NimbusSdkPlugin extends JavaPlugin implements Listener {

    private static NimbusSdkPlugin instance;
    private NimbusPermissionHandler permissionHandler;
    private NimbusChatRenderer chatRenderer;
    private NimbusNameTagHandler nameTagHandler;

    @Override
    public void onEnable() {
        instance = this;

        if (NimbusSelfService.isNimbusManaged()) {
            try {
                Nimbus.init();
                getLogger().info("Nimbus SDK initialized — service: " + Nimbus.name() + " (group: " + Nimbus.group() + ")");

                // Start permission handler
                String apiUrl = System.getProperty("nimbus.api.url");
                String token = System.getProperty("nimbus.api.token", "");
                if (apiUrl != null && !apiUrl.isEmpty()) {
                    permissionHandler = new NimbusPermissionHandler(this, apiUrl, token);
                    permissionHandler.start();

                    // Start chat renderer
                    chatRenderer = new NimbusChatRenderer(this, apiUrl, token);
                    chatRenderer.start();

                    // Start name tag handler
                    nameTagHandler = new NimbusNameTagHandler(this, apiUrl, token);
                    nameTagHandler.start();

                    getServer().getPluginManager().registerEvents(this, this);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to initialize Nimbus SDK: " + e.getMessage());
            }
        } else {
            getLogger().info("Nimbus SDK loaded — not running in a Nimbus-managed service");
        }
    }

    @Override
    public void onDisable() {
        if (permissionHandler != null) {
            permissionHandler.shutdown();
        }
        Nimbus.shutdown();
        instance = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (chatRenderer != null) {
            chatRenderer.fetchPlayerDisplay(event.getPlayer().getUniqueId());
        }
        if (nameTagHandler != null) {
            nameTagHandler.onJoin(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (chatRenderer != null) {
            chatRenderer.removePlayer(event.getPlayer().getUniqueId());
        }
        if (nameTagHandler != null) {
            nameTagHandler.onQuit(event.getPlayer());
        }
    }

    public static NimbusSdkPlugin getInstance() {
        return instance;
    }
}
