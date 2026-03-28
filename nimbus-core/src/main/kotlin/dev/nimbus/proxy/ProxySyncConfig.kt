package dev.nimbus.proxy

data class ProxySyncConfig(
    val tabList: TabListConfig = TabListConfig(),
    val motd: MotdConfig = MotdConfig(),
    val chat: ChatConfig = ChatConfig()
)

data class TabListConfig(
    val header: String = "<gradient:aqua:blue><bold>MY SERVER</bold></gradient>\n<gray>Online: <white>{online}</white>/{max}</gray>",
    val footer: String = "<gray>play.myserver.com</gray>",
    val playerFormat: String = "{prefix}{player}{suffix}",
    val updateInterval: Int = 5
)

data class MotdConfig(
    val line1: String = "<gradient:aqua:blue><bold>My Server Network</bold></gradient>",
    val line2: String = "<green>Welcome! {online} players online",
    val maxPlayers: Int = -1,
    val playerCountOffset: Int = 0
)

data class ChatConfig(
    val format: String = "{prefix}{player}{suffix} <dark_gray>» <gray>{message}",
    val enabled: Boolean = true
)
