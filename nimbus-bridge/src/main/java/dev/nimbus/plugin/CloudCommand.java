package dev.nimbus.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * /cloud command — In-game bridge to Nimbus Core REST API.
 * Permission: nimbus.cloud (base) + nimbus.cloud.<subcommand>
 */
public class CloudCommand implements SimpleCommand {

    private final NimbusApiClient api;
    private final dev.nimbus.sdk.NimbusClient sdkClient;

    private static final Map<String, String> SUBCOMMAND_PERMISSIONS = Map.ofEntries(
            Map.entry("help",        "nimbus.cloud"),
            Map.entry("list",        "nimbus.cloud.list"),
            Map.entry("status",      "nimbus.cloud.status"),
            Map.entry("start",       "nimbus.cloud.start"),
            Map.entry("stop",        "nimbus.cloud.stop"),
            Map.entry("restart",     "nimbus.cloud.restart"),
            Map.entry("exec",        "nimbus.cloud.exec"),
            Map.entry("players",     "nimbus.cloud.players"),
            Map.entry("send",        "nimbus.cloud.send"),
            Map.entry("groups",      "nimbus.cloud.groups"),
            Map.entry("info",        "nimbus.cloud.info"),
            Map.entry("setstate",    "nimbus.cloud.setstate"),
            Map.entry("reload",      "nimbus.cloud.reload"),
            Map.entry("shutdown",    "nimbus.cloud.shutdown"),
            Map.entry("perms",       "nimbus.cloud.perms"),
            Map.entry("maintenance", "nimbus.cloud.maintenance")
    );

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "list", "status", "start", "stop", "restart",
            "exec", "players", "send", "groups", "info", "setstate", "reload", "shutdown", "perms", "maintenance"
    );

    private static final List<String> MAINTENANCE_SUBCMDS = List.of(
            "status", "on", "off", "list", "add", "remove"
    );

    private static final List<String> PERMS_SUBCMDS = List.of("group", "user");
    private static final List<String> PERMS_GROUP_SUBCMDS = List.of(
            "list", "info", "create", "delete", "addperm", "removeperm", "setdefault", "addparent", "removeparent",
            "setprefix", "setsuffix", "setpriority"
    );
    private static final List<String> PERMS_USER_SUBCMDS = List.of("list", "info", "addgroup", "removegroup");

    private final com.velocitypowered.api.proxy.ProxyServer proxyServer;
    private volatile MaintenanceHandler maintenanceHandler;

    public CloudCommand(NimbusApiClient api, dev.nimbus.sdk.NimbusClient sdkClient, com.velocitypowered.api.proxy.ProxyServer proxyServer) {
        this.api = api;
        this.sdkClient = sdkClient;
        this.proxyServer = proxyServer;
    }

    public void setMaintenanceHandler(MaintenanceHandler handler) {
        this.maintenanceHandler = handler;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("nimbus.cloud");
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendHelp(invocation);
            return;
        }

        String sub = args[0].toLowerCase();

        // Permission check
        String perm = SUBCOMMAND_PERMISSIONS.get(sub);
        if (perm == null) {
            source.sendMessage(Component.text("Unknown subcommand: " + sub, NamedTextColor.RED));
            sendHelp(invocation);
            return;
        }
        if (!source.hasPermission(perm)) {
            source.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        switch (sub) {
            case "help"    -> sendHelp(invocation);
            case "list"    -> handleList(invocation);
            case "status"  -> handleStatus(invocation);
            case "start"   -> handleStart(invocation, args);
            case "stop"    -> handleStop(invocation, args);
            case "restart" -> handleRestart(invocation, args);
            case "exec"    -> handleExec(invocation, args);
            case "players" -> handlePlayers(invocation);
            case "send"    -> handleSend(invocation, args);
            case "groups"  -> handleGroups(invocation);
            case "info"    -> handleInfo(invocation, args);
            case "setstate"-> handleSetState(invocation, args);
            case "reload"  -> handleReload(invocation);
            case "shutdown"-> handleShutdown(invocation);
            case "perms"       -> handlePerms(invocation, args);
            case "maintenance" -> handleMaintenance(invocation, args);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .filter(s -> {
                        String perm = SUBCOMMAND_PERMISSIONS.get(s);
                        return perm != null && invocation.source().hasPermission(perm);
                    })
                    .toList();
        }

        String sub = args[0].toLowerCase();

        // Perms subcommand tab completion
        if (sub.equals("perms") && invocation.source().hasPermission("nimbus.cloud.perms")) {
            return suggestPerms(args);
        }

        // Maintenance subcommand tab completion
        if (sub.equals("maintenance") && invocation.source().hasPermission("nimbus.cloud.maintenance")) {
            return suggestMaintenance(args);
        }

        // For other subcommands that take service/group names
        return List.of();
    }

    // ── Subcommand handlers ──────────────────────────────────────────

    private void sendHelp(Invocation invocation) {
        var source = invocation.source();
        source.sendMessage(Component.empty());
        source.sendMessage(Component.text("  Nimbus Cloud Commands", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        source.sendMessage(Component.empty());

        record HelpEntry(String cmd, String desc, String perm) {}
        var entries = List.of(
                new HelpEntry("/cloud list",                  "List running services",     "nimbus.cloud.list"),
                new HelpEntry("/cloud status",                "Cluster overview",          "nimbus.cloud.status"),
                new HelpEntry("/cloud groups",                "List all groups",           "nimbus.cloud.groups"),
                new HelpEntry("/cloud info <group>",          "Group details",             "nimbus.cloud.info"),
                new HelpEntry("/cloud start <group>",         "Start a new instance",      "nimbus.cloud.start"),
                new HelpEntry("/cloud stop <service>",        "Stop a service",            "nimbus.cloud.stop"),
                new HelpEntry("/cloud restart <service>",     "Restart a service",         "nimbus.cloud.restart"),
                new HelpEntry("/cloud exec <service> <cmd>",  "Execute command on service","nimbus.cloud.exec"),
                new HelpEntry("/cloud players",               "List online players",       "nimbus.cloud.players"),
                new HelpEntry("/cloud send <player> <target>","Transfer a player",         "nimbus.cloud.send"),
                new HelpEntry("/cloud setstate <svc> <state>", "Set custom state on service","nimbus.cloud.setstate"),
                new HelpEntry("/cloud perms",                 "Manage permissions",        "nimbus.cloud.perms"),
                new HelpEntry("/cloud maintenance",           "Toggle maintenance mode",   "nimbus.cloud.maintenance"),
                new HelpEntry("/cloud reload",                "Reload group configs",      "nimbus.cloud.reload"),
                new HelpEntry("/cloud shutdown",              "Shutdown the cloud",        "nimbus.cloud.shutdown")
        );

        for (var entry : entries) {
            if (source.hasPermission(entry.perm())) {
                source.sendMessage(
                        Component.text("  " + entry.cmd(), NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.suggestCommand(entry.cmd().split(" <")[0]))
                                .append(Component.text(" — " + entry.desc(), NamedTextColor.GRAY))
                );
            }
        }
        source.sendMessage(Component.empty());
    }

    private void handleList(Invocation invocation) {
        var source = invocation.source();
        api.get("/api/services").thenAccept(result -> {
            if (!result.isSuccess()) {
                source.sendMessage(apiError(result));
                return;
            }
            JsonObject json = result.asJson();
            JsonArray services = json.getAsJsonArray("services");
            int count = json.get("count").getAsInt();

            source.sendMessage(Component.text("Services (" + count + ")", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

            if (count == 0) {
                source.sendMessage(Component.text("  No services running.", NamedTextColor.GRAY));
                return;
            }

            for (JsonElement el : services) {
                JsonObject svc = el.getAsJsonObject();
                String name = svc.get("name").getAsString();
                String state = svc.get("state").getAsString();
                int players = svc.get("playerCount").getAsInt();
                int port = svc.get("port").getAsInt();
                String customState = svc.has("customState") && !svc.get("customState").isJsonNull()
                        ? svc.get("customState").getAsString() : null;

                NamedTextColor stateColor = switch (state) {
                    case "READY" -> NamedTextColor.GREEN;
                    case "STARTING" -> NamedTextColor.YELLOW;
                    case "STOPPING" -> NamedTextColor.RED;
                    default -> NamedTextColor.GRAY;
                };

                var line = Component.text("  " + name, NamedTextColor.WHITE)
                        .append(Component.text(" [" + state + "]", stateColor));

                if (customState != null) {
                    NamedTextColor csColor = switch (customState) {
                        case "WAITING" -> NamedTextColor.AQUA;
                        case "INGAME" -> NamedTextColor.GOLD;
                        case "ENDING" -> NamedTextColor.LIGHT_PURPLE;
                        default -> NamedTextColor.YELLOW;
                    };
                    line = line.append(Component.text(" [" + customState + "]", csColor));
                }

                source.sendMessage(
                        line.append(Component.text(" " + players + " players", NamedTextColor.GRAY))
                                .append(Component.text(" :" + port, NamedTextColor.DARK_GRAY))
                );
            }
        });
    }

    private void handleStatus(Invocation invocation) {
        var source = invocation.source();
        api.get("/api/status").thenAccept(result -> {
            if (!result.isSuccess()) {
                source.sendMessage(apiError(result));
                return;
            }
            JsonObject json = result.asJson();

            String network = json.get("networkName").getAsString();
            boolean online = json.get("online").getAsBoolean();
            int totalServices = json.get("totalServices").getAsInt();
            int totalPlayers = json.get("totalPlayers").getAsInt();
            long uptime = json.get("uptimeSeconds").getAsLong();

            source.sendMessage(Component.empty());
            source.sendMessage(
                    Component.text("  " + network, NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                            .append(Component.text(online ? " ONLINE" : " OFFLINE", online ? NamedTextColor.GREEN : NamedTextColor.RED).decorate(TextDecoration.BOLD))
            );
            source.sendMessage(Component.text("  Services: ", NamedTextColor.GRAY).append(Component.text(totalServices, NamedTextColor.WHITE)));
            source.sendMessage(Component.text("  Players:  ", NamedTextColor.GRAY).append(Component.text(totalPlayers, NamedTextColor.WHITE)));
            source.sendMessage(Component.text("  Uptime:   ", NamedTextColor.GRAY).append(Component.text(formatUptime(uptime), NamedTextColor.WHITE)));

            JsonArray groups = json.getAsJsonArray("groups");
            if (groups != null && !groups.isEmpty()) {
                source.sendMessage(Component.empty());
                for (JsonElement el : groups) {
                    JsonObject g = el.getAsJsonObject();
                    String name = g.get("name").getAsString();
                    int instances = g.get("instances").getAsInt();
                    int maxInst = g.get("maxInstances").getAsInt();
                    int players = g.get("players").getAsInt();

                    source.sendMessage(
                            Component.text("  " + name, NamedTextColor.WHITE)
                                    .append(Component.text(" " + instances + "/" + maxInst + " instances", NamedTextColor.GRAY))
                                    .append(Component.text(" " + players + " players", NamedTextColor.GRAY))
                    );
                }
            }
            source.sendMessage(Component.empty());
        });
    }

    private void handleStart(Invocation invocation, String[] args) {
        var source = invocation.source();
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /cloud start <group>", NamedTextColor.RED));
            return;
        }
        String group = args[1];
        source.sendMessage(Component.text("Starting instance of " + group + "...", NamedTextColor.GRAY));
        api.post("/api/services/" + group + "/start").thenAccept(result -> {
            if (result.isSuccess()) {
                String msg = result.asJson().get("message").getAsString();
                source.sendMessage(Component.text(msg, NamedTextColor.GREEN));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleStop(Invocation invocation, String[] args) {
        var source = invocation.source();
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /cloud stop <service>", NamedTextColor.RED));
            return;
        }
        String service = args[1];
        source.sendMessage(Component.text("Stopping " + service + "...", NamedTextColor.GRAY));
        api.post("/api/services/" + service + "/stop").thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendMessage(Component.text("Service '" + service + "' stopped.", NamedTextColor.GREEN));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleRestart(Invocation invocation, String[] args) {
        var source = invocation.source();
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /cloud restart <service>", NamedTextColor.RED));
            return;
        }
        String service = args[1];
        source.sendMessage(Component.text("Restarting " + service + "...", NamedTextColor.GRAY));
        api.post("/api/services/" + service + "/restart").thenAccept(result -> {
            if (result.isSuccess()) {
                String msg = result.asJson().get("message").getAsString();
                source.sendMessage(Component.text(msg, NamedTextColor.GREEN));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleExec(Invocation invocation, String[] args) {
        var source = invocation.source();
        if (args.length < 3) {
            source.sendMessage(Component.text("Usage: /cloud exec <service> <command...>", NamedTextColor.RED));
            return;
        }
        String service = args[1];
        String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        JsonObject body = new JsonObject();
        body.addProperty("command", command);

        api.post("/api/services/" + service + "/exec", body).thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendMessage(
                        Component.text("Executed on " + service + ": ", NamedTextColor.GREEN)
                                .append(Component.text(command, NamedTextColor.WHITE))
                );
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handlePlayers(Invocation invocation) {
        var source = invocation.source();
        api.get("/api/players").thenAccept(result -> {
            if (!result.isSuccess()) {
                source.sendMessage(apiError(result));
                return;
            }
            JsonObject json = result.asJson();
            JsonArray players = json.getAsJsonArray("players");
            int count = json.get("count").getAsInt();

            source.sendMessage(Component.text("Players (" + count + ")", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

            if (count == 0) {
                source.sendMessage(Component.text("  No players online.", NamedTextColor.GRAY));
                return;
            }

            for (JsonElement el : players) {
                JsonObject p = el.getAsJsonObject();
                String name = p.get("name").getAsString();
                String server = p.get("server").getAsString();

                source.sendMessage(
                        Component.text("  " + name, NamedTextColor.WHITE)
                                .append(Component.text(" @ " + server, NamedTextColor.GRAY))
                );
            }
        });
    }

    private void handleSend(Invocation invocation, String[] args) {
        var source = invocation.source();
        if (args.length < 3) {
            source.sendMessage(Component.text("Usage: /cloud send <player> <target>", NamedTextColor.RED));
            return;
        }
        String player = args[1];
        String target = args[2];

        JsonObject body = new JsonObject();
        body.addProperty("targetService", target);

        api.post("/api/players/" + player + "/send", body).thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendMessage(
                        Component.text("Sent " + player + " to " + target, NamedTextColor.GREEN)
                );
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleGroups(Invocation invocation) {
        var source = invocation.source();
        api.get("/api/groups").thenAccept(result -> {
            if (!result.isSuccess()) {
                source.sendMessage(apiError(result));
                return;
            }
            JsonArray groups = result.asJson().getAsJsonArray("groups");

            source.sendMessage(Component.text("Groups (" + groups.size() + ")", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

            for (JsonElement el : groups) {
                JsonObject g = el.getAsJsonObject();
                String name = g.get("name").getAsString();
                String software = g.get("software").getAsString();
                String version = g.get("version").getAsString();

                source.sendMessage(
                        Component.text("  " + name, NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.runCommand("/cloud info " + name))
                                .append(Component.text(" " + software + " " + version, NamedTextColor.GRAY))
                );
            }
        });
    }

    private void handleInfo(Invocation invocation, String[] args) {
        var source = invocation.source();
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /cloud info <group>", NamedTextColor.RED));
            return;
        }
        String group = args[1];
        api.get("/api/groups/" + group).thenAccept(result -> {
            if (!result.isSuccess()) {
                source.sendMessage(apiError(result));
                return;
            }
            JsonObject g = result.asJson();
            String name = g.get("name").getAsString();
            String software = g.get("software").getAsString();
            String version = g.get("version").getAsString();
            int minInst = g.get("minInstances").getAsInt();
            int maxInst = g.get("maxInstances").getAsInt();
            boolean isStatic = g.get("static").getAsBoolean();

            JsonObject resources = g.getAsJsonObject("resources");
            String memory = resources.get("memory").getAsString();
            int maxPlayers = resources.get("maxPlayers").getAsInt();

            source.sendMessage(Component.empty());
            source.sendMessage(Component.text("  " + name, NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            source.sendMessage(Component.text("  Software:  ", NamedTextColor.GRAY).append(Component.text(software + " " + version, NamedTextColor.WHITE)));
            source.sendMessage(Component.text("  Instances: ", NamedTextColor.GRAY).append(Component.text(minInst + "-" + maxInst, NamedTextColor.WHITE)));
            source.sendMessage(Component.text("  Memory:    ", NamedTextColor.GRAY).append(Component.text(memory, NamedTextColor.WHITE)));
            source.sendMessage(Component.text("  Max slots: ", NamedTextColor.GRAY).append(Component.text(maxPlayers, NamedTextColor.WHITE)));
            source.sendMessage(Component.text("  Static:    ", NamedTextColor.GRAY).append(Component.text(isStatic ? "Yes" : "No", NamedTextColor.WHITE)));
            source.sendMessage(Component.empty());
        });
    }

    private void handleSetState(Invocation invocation, String[] args) {
        var source = invocation.source();
        if (args.length < 3) {
            source.sendMessage(Component.text("Usage: /cloud setstate <service> <state|clear>", NamedTextColor.RED));
            return;
        }
        String service = args[1];
        String state = args[2];

        if (state.equalsIgnoreCase("clear") || state.equalsIgnoreCase("null")) {
            sdkClient.clearCustomState(service).thenRun(() -> {
                source.sendMessage(Component.text("Cleared custom state on " + service, NamedTextColor.GREEN));
            }).exceptionally(e -> {
                source.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                return null;
            });
        } else {
            sdkClient.setCustomState(service, state.toUpperCase()).thenRun(() -> {
                source.sendMessage(
                        Component.text("Set custom state on " + service + ": ", NamedTextColor.GREEN)
                                .append(Component.text(state.toUpperCase(), NamedTextColor.WHITE))
                );
            }).exceptionally(e -> {
                source.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                return null;
            });
        }
    }

    private void handleReload(Invocation invocation) {
        var source = invocation.source();
        source.sendMessage(Component.text("Reloading group configs...", NamedTextColor.GRAY));
        api.post("/api/reload").thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendMessage(Component.text("Configs reloaded.", NamedTextColor.GREEN));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleShutdown(Invocation invocation) {
        var source = invocation.source();

        // Extra confirmation — only players can accidentally trigger this
        if (invocation.source() instanceof Player) {
            source.sendMessage(
                    Component.text("Are you sure? This will shut down the entire cloud.", NamedTextColor.RED).decorate(TextDecoration.BOLD)
            );
            source.sendMessage(
                    Component.text("  Click to confirm: ", NamedTextColor.GRAY)
                            .append(Component.text("[CONFIRM SHUTDOWN]", NamedTextColor.DARK_RED)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/cloud-shutdown-confirm")))
            );
            return;
        }

        executeShutdown(source);
    }

    void executeShutdown(com.velocitypowered.api.command.CommandSource source) {
        source.sendMessage(Component.text("Shutting down Nimbus...", NamedTextColor.RED));
        api.post("/api/shutdown").thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendMessage(Component.text("Shutdown initiated.", NamedTextColor.GREEN));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    // ── Perms ───────────────────────────────────────────────────────

    private void handlePerms(Invocation invocation, String[] args) {
        var source = invocation.source();

        if (args.length < 2) {
            sendPermsHelp(source);
            return;
        }

        String category = args[1].toLowerCase();
        switch (category) {
            case "group" -> handlePermsGroup(source, args);
            case "user" -> handlePermsUser(source, args);
            default -> sendPermsHelp(source);
        }
    }

    private void handlePermsGroup(com.velocitypowered.api.command.CommandSource source, String[] args) {
        if (args.length < 3) {
            source.sendMessage(Component.text("Usage: /cloud perms group <list|info|create|delete|addperm|removeperm|setdefault|addparent|removeparent>", NamedTextColor.RED));
            return;
        }

        String action = args[2].toLowerCase();
        switch (action) {
            case "list" -> {
                api.get("/api/permissions/groups").thenAccept(result -> {
                    if (!result.isSuccess()) { source.sendMessage(apiError(result)); return; }
                    JsonObject json = result.asJson();
                    JsonArray groups = json.getAsJsonArray("groups");

                    source.sendMessage(Component.text("Permission Groups (" + groups.size() + ")", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                    for (JsonElement el : groups) {
                        JsonObject g = el.getAsJsonObject();
                        String name = g.get("name").getAsString();
                        boolean isDefault = g.get("default").getAsBoolean();
                        int permCount = g.getAsJsonArray("permissions").size();

                        var line = Component.text("  " + name, NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.runCommand("/cloud perms group info " + name));
                        if (isDefault) line = line.append(Component.text(" [default]", NamedTextColor.GREEN));
                        line = line.append(Component.text(" " + permCount + " perm(s)", NamedTextColor.GRAY));
                        source.sendMessage(line);
                    }
                });
            }
            case "info" -> {
                if (args.length < 4) { source.sendMessage(Component.text("Usage: /cloud perms group info <name>", NamedTextColor.RED)); return; }
                String name = args[3];
                api.get("/api/permissions/groups/" + name).thenAccept(result -> {
                    if (!result.isSuccess()) { source.sendMessage(apiError(result)); return; }
                    JsonObject g = result.asJson();

                    source.sendMessage(Component.empty());
                    source.sendMessage(Component.text("  " + g.get("name").getAsString(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                    source.sendMessage(Component.text("  Default: ", NamedTextColor.GRAY)
                            .append(Component.text(g.get("default").getAsBoolean() ? "Yes" : "No", NamedTextColor.WHITE)));
                    source.sendMessage(Component.text("  Priority: ", NamedTextColor.GRAY)
                            .append(Component.text(g.has("priority") ? g.get("priority").getAsString() : "0", NamedTextColor.WHITE)));
                    String prefix = g.has("prefix") && !g.get("prefix").getAsString().isEmpty() ? g.get("prefix").getAsString() : "-";
                    String suffix = g.has("suffix") && !g.get("suffix").getAsString().isEmpty() ? g.get("suffix").getAsString() : "-";
                    source.sendMessage(Component.text("  Prefix: ", NamedTextColor.GRAY)
                            .append(Component.text(prefix, NamedTextColor.WHITE)));
                    source.sendMessage(Component.text("  Suffix: ", NamedTextColor.GRAY)
                            .append(Component.text(suffix, NamedTextColor.WHITE)));

                    JsonArray parents = g.getAsJsonArray("parents");
                    if (parents != null && !parents.isEmpty()) {
                        List<String> parentNames = new ArrayList<>();
                        parents.forEach(p -> parentNames.add(p.getAsString()));
                        source.sendMessage(Component.text("  Parents: ", NamedTextColor.GRAY)
                                .append(Component.text(String.join(", ", parentNames), NamedTextColor.WHITE)));
                    }

                    JsonArray perms = g.getAsJsonArray("permissions");
                    source.sendMessage(Component.text("  Permissions (" + perms.size() + "):", NamedTextColor.GRAY));
                    for (JsonElement p : perms) {
                        String perm = p.getAsString();
                        NamedTextColor color = perm.startsWith("-") ? NamedTextColor.RED : NamedTextColor.GREEN;
                        source.sendMessage(Component.text("    " + perm, color));
                    }
                    source.sendMessage(Component.empty());
                });
            }
            case "create" -> {
                if (args.length < 4) { source.sendMessage(Component.text("Usage: /cloud perms group create <name>", NamedTextColor.RED)); return; }
                JsonObject body = new JsonObject();
                body.addProperty("name", args[3]);
                api.post("/api/permissions/groups", body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Permission group '" + args[3] + "' created.", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "delete" -> {
                if (args.length < 4) { source.sendMessage(Component.text("Usage: /cloud perms group delete <name>", NamedTextColor.RED)); return; }
                api.delete("/api/permissions/groups/" + args[3]).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Permission group '" + args[3] + "' deleted.", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "addperm" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms group addperm <group> <permission>", NamedTextColor.RED)); return; }
                JsonObject body = new JsonObject();
                body.addProperty("permission", args[4]);
                api.post("/api/permissions/groups/" + args[3] + "/permissions", body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Added '" + args[4] + "' to '" + args[3] + "'.", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "removeperm" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms group removeperm <group> <permission>", NamedTextColor.RED)); return; }
                JsonObject body = new JsonObject();
                body.addProperty("permission", args[4]);
                api.delete("/api/permissions/groups/" + args[3] + "/permissions", body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Removed '" + args[4] + "' from '" + args[3] + "'.", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "setdefault" -> {
                if (args.length < 4) { source.sendMessage(Component.text("Usage: /cloud perms group setdefault <group> [true/false]", NamedTextColor.RED)); return; }
                boolean value = args.length < 5 || Boolean.parseBoolean(args[4]);
                JsonObject body = new JsonObject();
                body.addProperty("default", value);
                api.put("/api/permissions/groups/" + args[3], body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Group '" + args[3] + "' default set to " + value + ".", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "addparent" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms group addparent <group> <parent>", NamedTextColor.RED)); return; }
                // Fetch current group, add parent, update
                api.get("/api/permissions/groups/" + args[3]).thenAccept(result -> {
                    if (!result.isSuccess()) { source.sendMessage(apiError(result)); return; }
                    JsonObject current = result.asJson();
                    JsonArray parents = current.getAsJsonArray("parents");
                    List<String> parentList = new ArrayList<>();
                    parents.forEach(p -> parentList.add(p.getAsString()));
                    if (!parentList.contains(args[4])) parentList.add(args[4]);

                    JsonObject body = new JsonObject();
                    JsonArray newParents = new JsonArray();
                    parentList.forEach(newParents::add);
                    body.add("parents", newParents);

                    api.put("/api/permissions/groups/" + args[3], body).thenAccept(r2 -> {
                        if (r2.isSuccess()) source.sendMessage(Component.text("Added parent '" + args[4] + "' to '" + args[3] + "'.", NamedTextColor.GREEN));
                        else source.sendMessage(apiError(r2));
                    });
                });
            }
            case "removeparent" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms group removeparent <group> <parent>", NamedTextColor.RED)); return; }
                api.get("/api/permissions/groups/" + args[3]).thenAccept(result -> {
                    if (!result.isSuccess()) { source.sendMessage(apiError(result)); return; }
                    JsonObject current = result.asJson();
                    JsonArray parents = current.getAsJsonArray("parents");
                    List<String> parentList = new ArrayList<>();
                    parents.forEach(p -> parentList.add(p.getAsString()));
                    parentList.remove(args[4]);

                    JsonObject body = new JsonObject();
                    JsonArray newParents = new JsonArray();
                    parentList.forEach(newParents::add);
                    body.add("parents", newParents);

                    api.put("/api/permissions/groups/" + args[3], body).thenAccept(r2 -> {
                        if (r2.isSuccess()) source.sendMessage(Component.text("Removed parent '" + args[4] + "' from '" + args[3] + "'.", NamedTextColor.GREEN));
                        else source.sendMessage(apiError(r2));
                    });
                });
            }
            case "setprefix" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms group setprefix <group> <prefix...>", NamedTextColor.RED)); return; }
                String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                JsonObject body = new JsonObject();
                body.addProperty("prefix", prefix);
                api.put("/api/permissions/groups/" + args[3], body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Prefix for '" + args[3] + "' set to: " + prefix, NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "setsuffix" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms group setsuffix <group> <suffix...>", NamedTextColor.RED)); return; }
                String suffix = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                JsonObject body = new JsonObject();
                body.addProperty("suffix", suffix);
                api.put("/api/permissions/groups/" + args[3], body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Suffix for '" + args[3] + "' set to: " + suffix, NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "setpriority" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms group setpriority <group> <number>", NamedTextColor.RED)); return; }
                int priority;
                try { priority = Integer.parseInt(args[4]); }
                catch (NumberFormatException e) { source.sendMessage(Component.text("Priority must be a number.", NamedTextColor.RED)); return; }
                JsonObject body = new JsonObject();
                body.addProperty("priority", priority);
                api.put("/api/permissions/groups/" + args[3], body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Priority for '" + args[3] + "' set to " + priority + ".", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            default -> source.sendMessage(Component.text("Unknown perms group subcommand: " + action, NamedTextColor.RED));
        }
    }

    private void handlePermsUser(com.velocitypowered.api.command.CommandSource source, String[] args) {
        if (args.length < 3) {
            source.sendMessage(Component.text("Usage: /cloud perms user <list|info|addgroup|removegroup>", NamedTextColor.RED));
            return;
        }

        String action = args[2].toLowerCase();
        switch (action) {
            case "list" -> {
                // List all players with assignments — there's no dedicated endpoint, so use groups
                source.sendMessage(Component.text("Use the Nimbus console for a full player list.", NamedTextColor.GRAY));
                source.sendMessage(Component.text("Use '/cloud perms user info <uuid>' to look up a specific player.", NamedTextColor.GRAY));
            }
            case "info" -> {
                if (args.length < 4) { source.sendMessage(Component.text("Usage: /cloud perms user info <uuid|player>", NamedTextColor.RED)); return; }
                String identifier = args[3];

                // If it's a player name, try to resolve UUID from online players
                String uuid = resolveUuid(identifier);
                api.get("/api/permissions/players/" + uuid).thenAccept(result -> {
                    if (!result.isSuccess()) { source.sendMessage(apiError(result)); return; }
                    JsonObject json = result.asJson();

                    source.sendMessage(Component.empty());
                    source.sendMessage(Component.text("  " + json.get("name").getAsString(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                    source.sendMessage(Component.text("  UUID: ", NamedTextColor.GRAY).append(Component.text(json.get("uuid").getAsString(), NamedTextColor.WHITE)));

                    JsonArray groups = json.getAsJsonArray("groups");
                    List<String> groupNames = new ArrayList<>();
                    groups.forEach(g -> groupNames.add(g.getAsString()));
                    source.sendMessage(Component.text("  Groups: ", NamedTextColor.GRAY)
                            .append(Component.text(groupNames.isEmpty() ? "-" : String.join(", ", groupNames), NamedTextColor.WHITE)));

                    String dispPrefix = json.has("prefix") ? json.get("prefix").getAsString() : "";
                    String dispSuffix = json.has("suffix") ? json.get("suffix").getAsString() : "";
                    String dispGroup = json.has("displayGroup") ? json.get("displayGroup").getAsString() : "";
                    if (!dispPrefix.isEmpty() || !dispSuffix.isEmpty()) {
                        source.sendMessage(Component.text("  Display: ", NamedTextColor.GRAY)
                                .append(Component.text(dispPrefix + json.get("name").getAsString() + dispSuffix, NamedTextColor.WHITE))
                                .append(Component.text(" (" + dispGroup + ")", NamedTextColor.DARK_GRAY)));
                    }

                    JsonArray perms = json.getAsJsonArray("effectivePermissions");
                    source.sendMessage(Component.text("  Effective Permissions (" + perms.size() + "):", NamedTextColor.GRAY));
                    for (JsonElement p : perms) {
                        source.sendMessage(Component.text("    " + p.getAsString(), NamedTextColor.GREEN));
                    }
                    source.sendMessage(Component.empty());
                });
            }
            case "addgroup" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms user addgroup <uuid|player> <group>", NamedTextColor.RED)); return; }
                String identifier = args[3];
                String group = args[4];
                String uuid = resolveUuid(identifier);
                String playerName = resolvePlayerName(identifier);

                JsonObject body = new JsonObject();
                body.addProperty("group", group);
                body.addProperty("name", playerName);

                api.post("/api/permissions/players/" + uuid + "/groups", body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Added group '" + group + "' to " + playerName + ".", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            case "removegroup" -> {
                if (args.length < 5) { source.sendMessage(Component.text("Usage: /cloud perms user removegroup <uuid|player> <group>", NamedTextColor.RED)); return; }
                String identifier = args[3];
                String group = args[4];
                String uuid = resolveUuid(identifier);

                JsonObject body = new JsonObject();
                body.addProperty("group", group);

                api.delete("/api/permissions/players/" + uuid + "/groups", body).thenAccept(result -> {
                    if (result.isSuccess()) source.sendMessage(Component.text("Removed group '" + group + "' from player.", NamedTextColor.GREEN));
                    else source.sendMessage(apiError(result));
                });
            }
            default -> source.sendMessage(Component.text("Unknown perms user subcommand: " + action, NamedTextColor.RED));
        }
    }

    /**
     * Resolves a player name to UUID. If the identifier is already a UUID, returns it as-is.
     * Otherwise looks up the online player by name via Velocity.
     */
    private String resolveUuid(String identifier) {
        if (identifier.contains("-")) return identifier;
        // Look up online player by name
        Optional<Player> player = proxyServer.getPlayer(identifier);
        if (player.isPresent()) {
            return player.get().getUniqueId().toString();
        }
        return identifier; // Return as-is, API will treat as unknown
    }

    private String resolvePlayerName(String identifier) {
        if (!identifier.contains("-")) return identifier; // Already a name
        // Try to resolve UUID to online player name
        try {
            java.util.UUID uuid = java.util.UUID.fromString(identifier);
            Optional<Player> player = proxyServer.getPlayer(uuid);
            if (player.isPresent()) {
                return player.get().getUsername();
            }
        } catch (IllegalArgumentException ignored) {}
        return identifier;
    }

    private void sendPermsHelp(com.velocitypowered.api.command.CommandSource source) {
        source.sendMessage(Component.empty());
        source.sendMessage(Component.text("  Permission Commands", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        source.sendMessage(Component.empty());

        record Entry(String cmd, String desc) {}
        var entries = List.of(
                new Entry("/cloud perms group list", "List permission groups"),
                new Entry("/cloud perms group info <name>", "Group details"),
                new Entry("/cloud perms group create <name>", "Create a group"),
                new Entry("/cloud perms group delete <name>", "Delete a group"),
                new Entry("/cloud perms group addperm <group> <perm>", "Add permission"),
                new Entry("/cloud perms group removeperm <group> <perm>", "Remove permission"),
                new Entry("/cloud perms group setdefault <group>", "Set default group"),
                new Entry("/cloud perms group addparent <group> <parent>", "Add inheritance"),
                new Entry("/cloud perms group removeparent <group> <parent>", "Remove inheritance"),
                new Entry("/cloud perms group setprefix <group> <prefix...>", "Set display prefix"),
                new Entry("/cloud perms group setsuffix <group> <suffix...>", "Set display suffix"),
                new Entry("/cloud perms group setpriority <group> <number>", "Set display priority"),
                new Entry("/cloud perms user info <uuid|player>", "Player permissions"),
                new Entry("/cloud perms user addgroup <uuid|player> <group>", "Assign group"),
                new Entry("/cloud perms user removegroup <uuid|player> <group>", "Remove group")
        );

        for (var entry : entries) {
            source.sendMessage(
                    Component.text("  " + entry.cmd(), NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.suggestCommand(entry.cmd().split(" <")[0]))
                            .append(Component.text(" — " + entry.desc(), NamedTextColor.GRAY))
            );
        }
        source.sendMessage(Component.empty());
    }

    private List<String> suggestPerms(String[] args) {
        // args[0] = "perms", args[1] = category, args[2] = action, etc.
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return PERMS_SUBCMDS.stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 3) {
            String category = args[1].toLowerCase();
            String partial = args[2].toLowerCase();
            return switch (category) {
                case "group" -> PERMS_GROUP_SUBCMDS.stream().filter(s -> s.startsWith(partial)).toList();
                case "user" -> PERMS_USER_SUBCMDS.stream().filter(s -> s.startsWith(partial)).toList();
                default -> List.of();
            };
        }
        if (args.length == 4) {
            String category = args[1].toLowerCase();
            String action = args[2].toLowerCase();
            String partial = args[3].toLowerCase();

            if (category.equals("user") && List.of("info", "addgroup", "removegroup").contains(action)) {
                // Suggest online player names
                return proxyServer.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .toList();
            }
        }
        if (args.length == 5) {
            String category = args[1].toLowerCase();
            String action = args[2].toLowerCase();
            String partial = args[4].toLowerCase();

            // Suggest group names for addgroup/removegroup
            if (category.equals("user") && List.of("addgroup", "removegroup").contains(action)) {
                // We'd need cached group names — for now suggest nothing (would need async API call)
                return List.of();
            }
        }
        return List.of();
    }

    // ── Maintenance ────────────────────────────────────────────────────

    private void handleMaintenance(Invocation invocation, String[] args) {
        var source = invocation.source();

        if (args.length < 2) {
            sendMaintenanceHelp(source);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "status" -> handleMaintenanceStatus(source);
            case "on" -> {
                if (args.length >= 3) {
                    // /cloud maintenance on <group>
                    handleMaintenanceGroupToggle(source, args[2], true);
                } else {
                    handleMaintenanceGlobalToggle(source, true);
                }
            }
            case "off" -> {
                if (args.length >= 3) {
                    handleMaintenanceGroupToggle(source, args[2], false);
                } else {
                    handleMaintenanceGlobalToggle(source, false);
                }
            }
            case "list" -> handleMaintenanceWhitelistList(source);
            case "add" -> {
                if (args.length < 3) { source.sendMessage(Component.text("Usage: /cloud maintenance add <player>", NamedTextColor.RED)); return; }
                handleMaintenanceWhitelistAdd(source, args[2]);
            }
            case "remove" -> {
                if (args.length < 3) { source.sendMessage(Component.text("Usage: /cloud maintenance remove <player>", NamedTextColor.RED)); return; }
                handleMaintenanceWhitelistRemove(source, args[2]);
            }
            default -> {
                // Could be a group name: /cloud maintenance <group> on|off
                if (args.length >= 3) {
                    String groupName = args[1];
                    String toggle = args[2].toLowerCase();
                    if (toggle.equals("on")) {
                        handleMaintenanceGroupToggle(source, groupName, true);
                    } else if (toggle.equals("off")) {
                        handleMaintenanceGroupToggle(source, groupName, false);
                    } else {
                        sendMaintenanceHelp(source);
                    }
                } else {
                    sendMaintenanceHelp(source);
                }
            }
        }
    }

    private void handleMaintenanceStatus(com.velocitypowered.api.command.CommandSource source) {
        api.get("/api/maintenance").thenAccept(result -> {
            if (!result.isSuccess()) { source.sendMessage(apiError(result)); return; }
            JsonObject json = result.asJson();
            JsonObject global = json.getAsJsonObject("global");
            boolean globalEnabled = global != null && global.has("enabled") && global.get("enabled").getAsBoolean();

            source.sendMessage(Component.empty());
            source.sendMessage(Component.text("  Maintenance Mode", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            source.sendMessage(Component.empty());
            source.sendMessage(
                    Component.text("  Global: ", NamedTextColor.GRAY)
                            .append(globalEnabled
                                    ? Component.text("ENABLED", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                                    : Component.text("disabled", NamedTextColor.GRAY))
            );

            if (global != null && global.has("whitelist") && global.get("whitelist").isJsonArray()) {
                int wlSize = global.getAsJsonArray("whitelist").size();
                if (wlSize > 0) {
                    source.sendMessage(Component.text("  Whitelist: ", NamedTextColor.GRAY)
                            .append(Component.text(wlSize + " player(s)", NamedTextColor.WHITE)));
                }
            }

            JsonObject groups = json.getAsJsonObject("groups");
            if (groups != null && !groups.entrySet().isEmpty()) {
                source.sendMessage(Component.empty());
                source.sendMessage(Component.text("  Groups in Maintenance:", NamedTextColor.GRAY));
                for (var entry : groups.entrySet()) {
                    JsonObject groupObj = entry.getValue().getAsJsonObject();
                    if (groupObj.has("enabled") && groupObj.get("enabled").getAsBoolean()) {
                        source.sendMessage(
                                Component.text("    ! ", NamedTextColor.YELLOW)
                                        .append(Component.text(entry.getKey(), NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                        );
                    }
                }
            }
            source.sendMessage(Component.empty());
        });
    }

    private void handleMaintenanceGlobalToggle(com.velocitypowered.api.command.CommandSource source, boolean enabled) {
        JsonObject body = new JsonObject();
        body.addProperty("enabled", enabled);

        source.sendMessage(Component.text(enabled ? "Enabling global maintenance..." : "Disabling global maintenance...", NamedTextColor.GRAY));
        api.post("/api/maintenance/global", body).thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendMessage(Component.text(
                        enabled ? "Global maintenance enabled." : "Global maintenance disabled.",
                        enabled ? NamedTextColor.YELLOW : NamedTextColor.GREEN
                ));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleMaintenanceGroupToggle(com.velocitypowered.api.command.CommandSource source, String groupName, boolean enabled) {
        JsonObject body = new JsonObject();
        body.addProperty("enabled", enabled);

        source.sendMessage(Component.text(
                (enabled ? "Enabling" : "Disabling") + " maintenance for " + groupName + "...", NamedTextColor.GRAY));
        api.post("/api/maintenance/groups/" + groupName, body).thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendMessage(Component.text(
                        groupName + " maintenance " + (enabled ? "enabled." : "disabled."),
                        enabled ? NamedTextColor.YELLOW : NamedTextColor.GREEN
                ));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleMaintenanceWhitelistList(com.velocitypowered.api.command.CommandSource source) {
        api.get("/api/maintenance").thenAccept(result -> {
            if (!result.isSuccess()) { source.sendMessage(apiError(result)); return; }
            JsonObject json = result.asJson();
            JsonObject global = json.getAsJsonObject("global");

            source.sendMessage(Component.text("Maintenance Whitelist", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            if (global == null || !global.has("whitelist") || global.getAsJsonArray("whitelist").isEmpty()) {
                source.sendMessage(Component.text("  No whitelisted players.", NamedTextColor.GRAY));
                return;
            }

            for (var entry : global.getAsJsonArray("whitelist")) {
                source.sendMessage(
                        Component.text("  + ", NamedTextColor.GREEN)
                                .append(Component.text(entry.getAsString(), NamedTextColor.WHITE))
                );
            }
        });
    }

    private void handleMaintenanceWhitelistAdd(com.velocitypowered.api.command.CommandSource source, String player) {
        JsonObject body = new JsonObject();
        body.addProperty("entry", player);

        api.post("/api/maintenance/whitelist", body).thenAccept(result -> {
            if (result.isSuccess()) {
                String msg = result.asJson().get("message").getAsString();
                source.sendMessage(Component.text(msg, NamedTextColor.GREEN));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void handleMaintenanceWhitelistRemove(com.velocitypowered.api.command.CommandSource source, String player) {
        JsonObject body = new JsonObject();
        body.addProperty("entry", player);

        api.delete("/api/maintenance/whitelist", body).thenAccept(result -> {
            if (result.isSuccess()) {
                String msg = result.asJson().get("message").getAsString();
                source.sendMessage(Component.text(msg, NamedTextColor.GREEN));
            } else {
                source.sendMessage(apiError(result));
            }
        });
    }

    private void sendMaintenanceHelp(com.velocitypowered.api.command.CommandSource source) {
        source.sendMessage(Component.empty());
        source.sendMessage(Component.text("  Maintenance Commands", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        source.sendMessage(Component.empty());

        record Entry(String cmd, String desc) {}
        var entries = List.of(
                new Entry("/cloud maintenance status",              "Show maintenance status"),
                new Entry("/cloud maintenance on",                  "Enable global maintenance"),
                new Entry("/cloud maintenance off",                 "Disable global maintenance"),
                new Entry("/cloud maintenance on <group>",          "Enable group maintenance"),
                new Entry("/cloud maintenance off <group>",         "Disable group maintenance"),
                new Entry("/cloud maintenance list",                "Show whitelisted players"),
                new Entry("/cloud maintenance add <player>",        "Add player to whitelist"),
                new Entry("/cloud maintenance remove <player>",     "Remove player from whitelist")
        );

        for (var entry : entries) {
            source.sendMessage(
                    Component.text("  " + entry.cmd(), NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.suggestCommand(entry.cmd().split(" <")[0]))
                            .append(Component.text(" — " + entry.desc(), NamedTextColor.GRAY))
            );
        }
        source.sendMessage(Component.empty());
    }

    private List<String> suggestMaintenance(String[] args) {
        // args[0] = "maintenance", args[1] = subcommand, ...
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return MAINTENANCE_SUBCMDS.stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            if (sub.equals("on") || sub.equals("off")) {
                // Suggest group names for /cloud maintenance on|off <group>
                MaintenanceHandler mh = maintenanceHandler;
                // We could fetch groups from API, but for now suggest from proxy registered servers
                return proxyServer.getAllServers().stream()
                        .map(s -> deriveGroupName(s.getServerInfo().getName()))
                        .distinct()
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .toList();
            }
            if (sub.equals("add") || sub.equals("remove")) {
                // Suggest online player names
                return proxyServer.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .toList();
            }
        }
        return List.of();
    }

    private static String deriveGroupName(String serverName) {
        int lastDash = serverName.lastIndexOf('-');
        if (lastDash > 0) {
            String suffix = serverName.substring(lastDash + 1);
            try {
                Integer.parseInt(suffix);
                return serverName.substring(0, lastDash);
            } catch (NumberFormatException e) {
                return serverName;
            }
        }
        return serverName;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static Component apiError(NimbusApiClient.ApiResult result) {
        if (result.statusCode() == -1) {
            return Component.text("Could not reach Nimbus API: " + result.body(), NamedTextColor.RED);
        }
        try {
            JsonObject json = result.asJson();
            if (json.has("message")) {
                return Component.text(json.get("message").getAsString(), NamedTextColor.RED);
            }
        } catch (Exception ignored) {}
        return Component.text("API error (HTTP " + result.statusCode() + ")", NamedTextColor.RED);
    }

    private static String formatUptime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m " + secs + "s";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }
}
