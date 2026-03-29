package dev.nimbus.proxy

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/**
 * Manages proxy sync configuration and maintenance state.
 * Config is split across three files in `config/modules/syncproxy/`:
 * - `motd.toml` — MOTD settings + maintenance mode config
 * - `tablist.toml` — Tab list header, footer, player format
 * - `chat.toml` — Chat format settings
 *
 * Player tab overrides are ephemeral (in-memory only).
 */
class ProxySyncManager(private val proxyDir: Path) {

    private val logger = LoggerFactory.getLogger(ProxySyncManager::class.java)

    private var config = ProxySyncConfig()
    private val playerTabOverrides = ConcurrentHashMap<String, String>()

    // ── Maintenance State ──────────────────────────────────────────

    @Volatile var globalMaintenanceEnabled: Boolean = false
        private set

    @Volatile var globalMotdLine1: String = "  <gradient:#ff6b6b:#ee5a24><bold>MAINTENANCE</bold></gradient>"
        private set

    @Volatile var globalMotdLine2: String = "  <gray>We are currently performing maintenance.</gray>"
        private set

    @Volatile var globalProtocolText: String = "Maintenance"
        private set

    @Volatile var globalKickMessage: String = "<red><bold>Maintenance</bold></red>\n<gray>The server is currently under maintenance.\nPlease try again later.</gray>"
        private set

    private val maintenanceWhitelist = ConcurrentHashMap.newKeySet<String>()
    private val groupMaintenance = ConcurrentHashMap<String, GroupMaintenanceState>()

    data class GroupMaintenanceState(
        val enabled: Boolean = false,
        val kickMessage: String = "<red>This game mode is currently under maintenance.</red>"
    )

    // ── Initialization ─────────────────────────────────────────────

    fun init() {
        if (!proxyDir.exists()) proxyDir.createDirectories()

        val motdFile = proxyDir.resolve("motd.toml")
        val tablistFile = proxyDir.resolve("tablist.toml")
        val chatFile = proxyDir.resolve("chat.toml")

        if (!motdFile.exists()) {
            motdFile.writeText(buildMotdToml())
            logger.info("Created default motd.toml")
        }
        if (!tablistFile.exists()) {
            tablistFile.writeText(buildTablistToml())
            logger.info("Created default tablist.toml")
        }
        if (!chatFile.exists()) {
            chatFile.writeText(buildChatToml())
            logger.info("Created default chat.toml")
        }

        reload()
    }

    fun reload() {
        val motdFile = proxyDir.resolve("motd.toml")
        val tablistFile = proxyDir.resolve("tablist.toml")
        val chatFile = proxyDir.resolve("chat.toml")

        var motd = MotdConfig()
        var tabList = TabListConfig()
        var chat = ChatConfig()

        if (motdFile.exists()) {
            try {
                val parsed = parseMotdToml(motdFile.readText())
                motd = parsed.first
                // Maintenance state loaded as side effect
            } catch (e: Exception) {
                logger.warn("Failed to load motd.toml: {}", e.message)
            }
        }

        if (tablistFile.exists()) {
            try {
                tabList = parseTablistToml(tablistFile.readText())
            } catch (e: Exception) {
                logger.warn("Failed to load tablist.toml: {}", e.message)
            }
        }

        if (chatFile.exists()) {
            try {
                chat = parseChatToml(chatFile.readText())
            } catch (e: Exception) {
                logger.warn("Failed to load chat.toml: {}", e.message)
            }
        }

        config = ProxySyncConfig(tabList, motd, chat)
        logger.info("Loaded proxy sync config (motd + tablist + chat)")
    }

    fun getConfig(): ProxySyncConfig = config

    // ── Tab List ───────────────────────────────────────────────────

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
        saveTablist()
    }

    // ── MOTD ───────────────────────────────────────────────────────

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
        saveMotd()
    }

    // ── Chat ───────────────────────────────────────────────────────

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
        saveChat()
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

    // ── Global Maintenance ─────────────────────────────────────────

    fun setGlobalMaintenance(enabled: Boolean): Boolean {
        if (globalMaintenanceEnabled == enabled) return false
        globalMaintenanceEnabled = enabled
        saveMotd()
        return true
    }

    fun updateGlobalMaintenanceConfig(
        motdLine1: String? = null,
        motdLine2: String? = null,
        protocolText: String? = null,
        kickMessage: String? = null
    ) {
        if (motdLine1 != null) globalMotdLine1 = motdLine1
        if (motdLine2 != null) globalMotdLine2 = motdLine2
        if (protocolText != null) globalProtocolText = protocolText
        if (kickMessage != null) globalKickMessage = kickMessage
        saveMotd()
    }

    // ── Maintenance Whitelist ──────────────────────────────────────

    fun addToMaintenanceWhitelist(entry: String): Boolean {
        val added = maintenanceWhitelist.add(entry.lowercase())
        if (added) saveMotd()
        return added
    }

    fun removeFromMaintenanceWhitelist(entry: String): Boolean {
        val removed = maintenanceWhitelist.remove(entry.lowercase())
        if (removed) saveMotd()
        return removed
    }

    fun isMaintenanceWhitelisted(nameOrUuid: String): Boolean =
        maintenanceWhitelist.contains(nameOrUuid.lowercase())

    fun getMaintenanceWhitelist(): Set<String> = maintenanceWhitelist.toSet()

    // ── Group Maintenance ──────────────────────────────────────────

    fun setGroupMaintenance(groupName: String, enabled: Boolean): Boolean {
        val current = groupMaintenance[groupName]
        if (current?.enabled == enabled) return false
        if (enabled) {
            groupMaintenance[groupName] = (current ?: GroupMaintenanceState()).copy(enabled = true)
        } else {
            groupMaintenance.remove(groupName)
        }
        saveMotd()
        return true
    }

    fun updateGroupMaintenanceConfig(groupName: String, kickMessage: String) {
        val current = groupMaintenance[groupName] ?: GroupMaintenanceState()
        groupMaintenance[groupName] = current.copy(kickMessage = kickMessage)
        saveMotd()
    }

    fun isGroupInMaintenance(groupName: String): Boolean =
        groupMaintenance[groupName]?.enabled == true

    fun getGroupMaintenanceState(groupName: String): GroupMaintenanceState? = groupMaintenance[groupName]

    fun getAllGroupMaintenanceStates(): Map<String, GroupMaintenanceState> = groupMaintenance.toMap()

    fun getMaintenanceGroups(): List<String> =
        groupMaintenance.entries.filter { it.value.enabled }.map { it.key }

    // ── TOML Save ──────────────────────────────────────────────────

    private fun saveMotd() {
        proxyDir.resolve("motd.toml").writeText(buildMotdToml())
    }

    private fun saveTablist() {
        proxyDir.resolve("tablist.toml").writeText(buildTablistToml())
    }

    private fun saveChat() {
        proxyDir.resolve("chat.toml").writeText(buildChatToml())
    }

    // ── TOML Builders ──────────────────────────────────────────────

    private fun buildMotdToml(): String = buildString {
        appendLine("# Nimbus MOTD Configuration")
        appendLine("# Controls the server list MOTD shown in the Minecraft multiplayer screen.")
        appendLine("# Supports MiniMessage formatting and placeholders: {online}, {max}")
        appendLine()
        appendLine("[motd]")
        appendLine("line1 = ${tomlString(config.motd.line1)}")
        appendLine("line2 = ${tomlString(config.motd.line2)}")
        appendLine("max_players = ${config.motd.maxPlayers}")
        appendLine("player_count_offset = ${config.motd.playerCountOffset}")
        appendLine()
        appendLine("# ── Maintenance Mode ────────────────────────────────────────")
        appendLine("# When enabled, the proxy shows a maintenance MOTD and blocks new connections.")
        appendLine("# Players on the whitelist or with 'nimbus.maintenance.bypass' can still join.")
        appendLine()
        appendLine("[maintenance]")
        appendLine("enabled = $globalMaintenanceEnabled")
        appendLine("motd_line1 = ${tomlString(globalMotdLine1)}")
        appendLine("motd_line2 = ${tomlString(globalMotdLine2)}")
        appendLine("protocol_text = ${tomlString(globalProtocolText)}")
        appendLine("kick_message = ${tomlString(globalKickMessage)}")
        appendLine("whitelist = [${maintenanceWhitelist.joinToString(", ") { tomlString(it) }}]")

        for ((name, state) in groupMaintenance) {
            if (!state.enabled) continue
            appendLine()
            appendLine("[maintenance.groups.$name]")
            appendLine("enabled = ${state.enabled}")
            appendLine("kick_message = ${tomlString(state.kickMessage)}")
        }
    }

    private fun buildTablistToml(): String = buildString {
        appendLine("# Nimbus Tab List Configuration")
        appendLine("# Controls the tab list header, footer, and player name format.")
        appendLine("# Supports MiniMessage formatting and placeholders:")
        appendLine("#   {online}, {max}, {player}, {server}, {group}, {prefix}, {suffix}")
        appendLine()
        appendLine("[tablist]")
        appendLine("header = ${tomlString(config.tabList.header)}")
        appendLine("footer = ${tomlString(config.tabList.footer)}")
        appendLine("player_format = ${tomlString(config.tabList.playerFormat)}")
        appendLine("update_interval = ${config.tabList.updateInterval}")
    }

    private fun buildChatToml(): String = buildString {
        appendLine("# Nimbus Chat Configuration")
        appendLine("# Controls the proxy-wide chat format.")
        appendLine("# Supports MiniMessage formatting and placeholders:")
        appendLine("#   {player}, {message}, {prefix}, {suffix}, {server}, {group}")
        appendLine()
        appendLine("[chat]")
        appendLine("format = ${tomlString(config.chat.format)}")
        appendLine("enabled = ${config.chat.enabled}")
    }

    // ── TOML Parsers ───────────────────────────────────────────────

    private fun parseMotdToml(content: String): Pair<MotdConfig, Unit> {
        var line1 = ""
        var line2 = ""
        var maxPlayers = -1
        var playerCountOffset = 0

        var section = ""
        var groupSection = ""
        val newWhitelist = mutableSetOf<String>()
        val newGroups = ConcurrentHashMap<String, GroupMaintenanceState>()
        var tempGroupEnabled = false
        var tempGroupKickMessage = "<red>This game mode is currently under maintenance.</red>"

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                // Save previous group if any
                if (groupSection.isNotEmpty()) {
                    newGroups[groupSection] = GroupMaintenanceState(tempGroupEnabled, tempGroupKickMessage)
                    tempGroupEnabled = false
                    tempGroupKickMessage = "<red>This game mode is currently under maintenance.</red>"
                }

                val sectionName = line.removePrefix("[").removeSuffix("]").trim()
                if (sectionName.startsWith("maintenance.groups.")) {
                    section = "maintenance.groups"
                    groupSection = sectionName.removePrefix("maintenance.groups.")
                } else {
                    section = sectionName
                    groupSection = ""
                }
                continue
            }

            val eqIndex = line.indexOf('=')
            if (eqIndex < 0) continue

            val key = line.substring(0, eqIndex).trim()
            val rawValue = line.substring(eqIndex + 1).trim()

            when (section) {
                "motd" -> when (key) {
                    "line1" -> line1 = extractTomlString(rawValue) ?: rawValue
                    "line2" -> line2 = extractTomlString(rawValue) ?: rawValue
                    "max_players" -> maxPlayers = rawValue.toIntOrNull() ?: -1
                    "player_count_offset" -> playerCountOffset = rawValue.toIntOrNull() ?: 0
                }
                "maintenance" -> when (key) {
                    "enabled" -> globalMaintenanceEnabled = rawValue.toBooleanStrictOrNull() ?: false
                    "motd_line1" -> globalMotdLine1 = extractTomlString(rawValue) ?: rawValue
                    "motd_line2" -> globalMotdLine2 = extractTomlString(rawValue) ?: rawValue
                    "protocol_text" -> globalProtocolText = extractTomlString(rawValue) ?: rawValue
                    "kick_message" -> globalKickMessage = extractTomlString(rawValue) ?: rawValue
                    "whitelist" -> {
                        val arrayContent = rawValue.removePrefix("[").removeSuffix("]").trim()
                        if (arrayContent.isNotEmpty()) {
                            arrayContent.split(",").forEach { entry ->
                                val clean = extractTomlString(entry.trim()) ?: entry.trim()
                                if (clean.isNotEmpty()) newWhitelist.add(clean.lowercase())
                            }
                        }
                    }
                }
                "maintenance.groups" -> when (key) {
                    "enabled" -> tempGroupEnabled = rawValue.toBooleanStrictOrNull() ?: false
                    "kick_message" -> tempGroupKickMessage = extractTomlString(rawValue) ?: rawValue
                }
            }
        }

        // Save last group section
        if (groupSection.isNotEmpty()) {
            newGroups[groupSection] = GroupMaintenanceState(tempGroupEnabled, tempGroupKickMessage)
        }

        maintenanceWhitelist.clear()
        maintenanceWhitelist.addAll(newWhitelist)
        groupMaintenance.clear()
        groupMaintenance.putAll(newGroups)

        return Pair(MotdConfig(line1, line2, maxPlayers, playerCountOffset), Unit)
    }

    private fun parseTablistToml(content: String): TabListConfig {
        var header = ""
        var footer = ""
        var playerFormat = ""
        var updateInterval = 5

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

            if (section == "tablist") {
                when (key) {
                    "header" -> header = extractTomlString(rawValue) ?: rawValue
                    "footer" -> footer = extractTomlString(rawValue) ?: rawValue
                    "player_format" -> playerFormat = extractTomlString(rawValue) ?: rawValue
                    "update_interval" -> updateInterval = rawValue.toIntOrNull() ?: 5
                }
            }
        }

        return TabListConfig(header, footer, playerFormat, updateInterval)
    }

    private fun parseChatToml(content: String): ChatConfig {
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

            if (section == "chat") {
                when (key) {
                    "format" -> chatFormat = extractTomlString(rawValue) ?: rawValue
                    "enabled" -> chatEnabled = rawValue.toBooleanStrictOrNull() ?: true
                }
            }
        }

        return ChatConfig(chatFormat, chatEnabled)
    }

    // ── TOML Helpers ───────────────────────────────────────────────

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
