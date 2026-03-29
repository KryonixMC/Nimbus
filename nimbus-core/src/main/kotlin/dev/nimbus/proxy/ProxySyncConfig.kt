package dev.nimbus.proxy

data class ProxySyncConfig(
    val tabList: TabListConfig = TabListConfig(),
    val motd: MotdConfig = MotdConfig(),
    val chat: ChatConfig = ChatConfig()
)

data class TabListConfig(
    val header: String = "\n<gradient:#58a6ff:#56d4dd><bold>☁ NIMBUS CLOUD</bold></gradient>\n",
    val footer: String = "\n<gray>Online</gray> <white>»</white> <gradient:#56d4dd:#b392f0>{online}</gradient><dark_gray>/</dark_gray><gray>{max}</gray>\n",
    val playerFormat: String = "{prefix}{player}{suffix}",
    val updateInterval: Int = 5
)

data class MotdConfig(
    val line1: String = "  <gradient:#58a6ff:#56d4dd:#b392f0><bold>☁ NIMBUS CLOUD</bold></gradient>",
    val line2: String = "  <gray>» </gray><gradient:#56d364:#56d4dd>{online} players online</gradient>",
    val maxPlayers: Int = -1,
    val playerCountOffset: Int = 0
)

data class ChatConfig(
    val format: String = "{prefix}{player}{suffix} <dark_gray>» <gray>{message}",
    val enabled: Boolean = true
)

// ── Maintenance ────────────────────────────────────────────────────

data class MaintenanceConfig(
    val global: GlobalMaintenanceConfig = GlobalMaintenanceConfig(),
    val groups: Map<String, GroupMaintenanceConfig> = emptyMap()
)

data class GlobalMaintenanceConfig(
    val enabled: Boolean = false,
    val motdLine1: String = "  <gradient:#ff6b6b:#ee5a24><bold>MAINTENANCE</bold></gradient>",
    val motdLine2: String = "  <gray>We are currently performing maintenance.</gray>",
    val protocolText: String = "Maintenance",
    val kickMessage: String = "<red><bold>Maintenance</bold></red>\n<gray>The server is currently under maintenance.\nPlease try again later.</gray>",
    val whitelist: List<String> = emptyList()
)

data class GroupMaintenanceConfig(
    val enabled: Boolean = false,
    val kickMessage: String = "<red>This game mode is currently under maintenance.</red>"
)
