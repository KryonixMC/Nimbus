package dev.nimbus.proxy

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/**
 * Manages proxy sync configuration (tab list + MOTD) and per-player tab overrides.
 * Config is stored as TOML in `config/modules/syncproxy/proxy.toml`. Player overrides are ephemeral (in-memory only).
 */
class ProxySyncManager(private val proxyDir: Path) {

    private val logger = LoggerFactory.getLogger(ProxySyncManager::class.java)

    private var config = ProxySyncConfig()
    private val playerTabOverrides = ConcurrentHashMap<String, String>()

    fun init() {
        if (!proxyDir.exists()) proxyDir.createDirectories()
        val configFile = proxyDir.resolve("proxy.toml")
        if (!configFile.exists()) {
            configFile.writeText(buildProxyToml(config))
            logger.info("Created default proxy.toml")
        }
        reload()
    }

    fun reload() {
        val configFile = proxyDir.resolve("proxy.toml")
        if (!configFile.exists()) return
        try {
            config = parseProxyToml(configFile.readText())
            logger.info("Loaded proxy sync config")
        } catch (e: Exception) {
            logger.warn("Failed to load proxy.toml: {}", e.message)
        }
    }

    fun getConfig(): ProxySyncConfig = config

    fun updateTabList(
        header: String? = null,
        footer: String? = null,
        playerFormat: String? = null,
        updateInterval: Int? = null
    ) {
        config = config.copy(
            tabList = config.tabList.copy(
                header = header ?: config.tabList.header,
                footer = footer ?: config.tabList.footer,
                playerFormat = playerFormat ?: config.tabList.playerFormat,
                updateInterval = updateInterval ?: config.tabList.updateInterval
            )
        )
        save()
    }

    fun updateChat(
        format: String? = null,
        enabled: Boolean? = null
    ) {
        config = config.copy(
            chat = config.chat.copy(
                format = format ?: config.chat.format,
                enabled = enabled ?: config.chat.enabled
            )
        )
        save()
    }

    fun updateMotd(
        line1: String? = null,
        line2: String? = null,
        maxPlayers: Int? = null,
        playerCountOffset: Int? = null
    ) {
        config = config.copy(
            motd = config.motd.copy(
                line1 = line1 ?: config.motd.line1,
                line2 = line2 ?: config.motd.line2,
                maxPlayers = maxPlayers ?: config.motd.maxPlayers,
                playerCountOffset = playerCountOffset ?: config.motd.playerCountOffset
            )
        )
        save()
    }

    // ── Player Tab Overrides (ephemeral) ───────────────────────────

    fun setPlayerTabFormat(uuid: String, format: String) {
        playerTabOverrides[uuid] = format
    }

    fun clearPlayerTabFormat(uuid: String) {
        playerTabOverrides.remove(uuid)
    }

    fun getPlayerTabFormat(uuid: String): String? = playerTabOverrides[uuid]

    fun getAllPlayerTabOverrides(): Map<String, String> = playerTabOverrides.toMap()

    // ── TOML I/O ───────────────────────────────────────────────────

    private fun save() {
        val configFile = proxyDir.resolve("proxy.toml")
        configFile.writeText(buildProxyToml(config))
    }

    private fun buildProxyToml(cfg: ProxySyncConfig): String = buildString {
        appendLine("# Nimbus Proxy Sync Configuration")
        appendLine("# Controls tab list and MOTD across all proxy instances.")
        appendLine("# Changes are synced in real-time via the API.")
        appendLine()
        appendLine("[tablist]")
        appendLine("header = ${tomlString(cfg.tabList.header)}")
        appendLine("footer = ${tomlString(cfg.tabList.footer)}")
        appendLine("player_format = ${tomlString(cfg.tabList.playerFormat)}")
        appendLine("update_interval = ${cfg.tabList.updateInterval}")
        appendLine()
        appendLine("[motd]")
        appendLine("line1 = ${tomlString(cfg.motd.line1)}")
        appendLine("line2 = ${tomlString(cfg.motd.line2)}")
        appendLine("max_players = ${cfg.motd.maxPlayers}")
        appendLine("player_count_offset = ${cfg.motd.playerCountOffset}")
        appendLine()
        appendLine("[chat]")
        appendLine("format = ${tomlString(cfg.chat.format)}")
        appendLine("enabled = ${cfg.chat.enabled}")
    }

    private fun parseProxyToml(content: String): ProxySyncConfig {
        var header = ""
        var footer = ""
        var playerFormat = ""
        var updateInterval = 5
        var line1 = ""
        var line2 = ""
        var maxPlayers = -1
        var playerCountOffset = 0
        var chatFormat = "{prefix}{player}{suffix} <dark_gray>» <gray>{message}"
        var chatEnabled = true

        var section = ""

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.removePrefix("[").removeSuffix("]").trim()
                continue
            }

            val eqIndex = line.indexOf('=')
            if (eqIndex < 0) continue

            val key = line.substring(0, eqIndex).trim()
            val rawValue = line.substring(eqIndex + 1).trim()

            when (section) {
                "tablist" -> when (key) {
                    "header" -> header = extractTomlString(rawValue) ?: rawValue
                    "footer" -> footer = extractTomlString(rawValue) ?: rawValue
                    "player_format" -> playerFormat = extractTomlString(rawValue) ?: rawValue
                    "update_interval" -> updateInterval = rawValue.toIntOrNull() ?: 5
                }
                "motd" -> when (key) {
                    "line1" -> line1 = extractTomlString(rawValue) ?: rawValue
                    "line2" -> line2 = extractTomlString(rawValue) ?: rawValue
                    "max_players" -> maxPlayers = rawValue.toIntOrNull() ?: -1
                    "player_count_offset" -> playerCountOffset = rawValue.toIntOrNull() ?: 0
                }
                "chat" -> when (key) {
                    "format" -> chatFormat = extractTomlString(rawValue) ?: rawValue
                    "enabled" -> chatEnabled = rawValue.toBooleanStrictOrNull() ?: true
                }
            }
        }

        return ProxySyncConfig(
            tabList = TabListConfig(header, footer, playerFormat, updateInterval),
            motd = MotdConfig(line1, line2, maxPlayers, playerCountOffset),
            chat = ChatConfig(chatFormat, chatEnabled)
        )
    }

    private fun tomlString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun extractTomlString(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }
        return null
    }
}
