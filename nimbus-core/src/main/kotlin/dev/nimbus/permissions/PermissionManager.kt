package dev.nimbus.permissions

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Manages permission groups and player-to-group assignments.
 * Data is stored as TOML files in a `permissions/` directory.
 */
class PermissionManager(private val permissionsDir: Path) {

    private val logger = LoggerFactory.getLogger(PermissionManager::class.java)

    private val groups = mutableMapOf<String, PermissionGroup>()
    private val players = mutableMapOf<String, PlayerEntry>() // UUID -> PlayerEntry

    fun init() {
        if (!permissionsDir.exists()) permissionsDir.createDirectories()
        reload()
        ensureDefaultGroup()
    }

    /**
     * Creates a "Default" group on first run if no groups exist yet.
     */
    private fun ensureDefaultGroup() {
        if (groups.isNotEmpty()) return
        logger.info("No permission groups found — creating default group")
        createGroup("Default", default = true)
    }

    fun reload() {
        groups.clear()
        players.clear()
        loadGroups()
        loadPlayers()
        logger.info("Loaded {} permission group(s), {} player assignment(s)", groups.size, players.size)
    }

    // ── Group CRUD ──────────────────────────────────────────────

    fun getAllGroups(): List<PermissionGroup> = groups.values.toList()

    fun getGroup(name: String): PermissionGroup? =
        groups.values.find { it.name.equals(name, ignoreCase = true) }

    fun getDefaultGroup(): PermissionGroup? =
        groups.values.find { it.default }

    fun createGroup(name: String, default: Boolean = false): PermissionGroup {
        require(getGroup(name) == null) { "Group '$name' already exists" }
        val group = PermissionGroup(name = name, default = default)
        groups[name.lowercase()] = group
        saveGroup(group)
        return group
    }

    fun deleteGroup(name: String) {
        val group = getGroup(name) ?: throw IllegalArgumentException("Group '$name' not found")
        groups.remove(group.name.lowercase())
        val file = permissionsDir.resolve("${group.name.lowercase()}.toml")
        file.deleteIfExists()

        // Remove group from all players
        players.values.forEach { it.groups.removeAll { g -> g.equals(name, ignoreCase = true) } }
        savePlayers()
    }

    fun addPermission(groupName: String, permission: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        if (permission !in group.permissions) {
            group.permissions.add(permission)
            saveGroup(group)
        }
    }

    fun removePermission(groupName: String, permission: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        if (group.permissions.remove(permission)) {
            saveGroup(group)
        }
    }

    fun setDefault(groupName: String, default: Boolean) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        // Clear default from other groups if setting this one as default
        if (default) {
            groups.values.filter { it.default && it.name != group.name }.forEach {
                groups[it.name.lowercase()] = it.copy(default = false)
                saveGroup(groups[it.name.lowercase()]!!)
            }
        }
        groups[group.name.lowercase()] = group.copy(default = default)
        saveGroup(groups[group.name.lowercase()]!!)
    }

    fun addParent(groupName: String, parentName: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        getGroup(parentName) ?: throw IllegalArgumentException("Parent group '$parentName' not found")
        if (!group.parents.any { it.equals(parentName, ignoreCase = true) }) {
            group.parents.add(parentName)
            saveGroup(group)
        }
    }

    fun removeParent(groupName: String, parentName: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        if (group.parents.removeAll { it.equals(parentName, ignoreCase = true) }) {
            saveGroup(group)
        }
    }

    // ── Player CRUD ─────────────────────────────────────────────

    fun getAllPlayers(): Map<String, PlayerEntry> = players.toMap()

    fun getPlayer(uuid: String): PlayerEntry? = players[uuid]

    fun getPlayerByName(name: String): Pair<String, PlayerEntry>? =
        players.entries.find { it.value.name.equals(name, ignoreCase = true) }?.let { it.key to it.value }

    /**
     * Registers a player by UUID and name. Creates entry if not exists, updates name if it changed.
     * Returns true if the player was newly created or the name was updated.
     */
    fun registerPlayer(uuid: String, playerName: String): Boolean {
        val existing = players[uuid]
        if (existing != null && existing.name == playerName) return false // already up-to-date

        players[uuid] = existing?.copy(name = playerName) ?: PlayerEntry(name = playerName)
        savePlayers()
        return true
    }

    fun setPlayerGroup(uuid: String, playerName: String, groupName: String) {
        getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        val entry = players.getOrPut(uuid) { PlayerEntry(name = playerName) }
        if (!entry.groups.any { it.equals(groupName, ignoreCase = true) }) {
            entry.groups.add(groupName)
        }
        // Update cached display name
        players[uuid] = entry.copy(name = playerName)
        savePlayers()
    }

    fun removePlayerGroup(uuid: String, groupName: String) {
        val entry = players[uuid] ?: throw IllegalArgumentException("Player not found")
        if (entry.groups.removeAll { it.equals(groupName, ignoreCase = true) }) {
            savePlayers()
        }
    }

    // ── Permission Resolution ───────────────────────────────────

    /**
     * Returns all effective permissions for a player, resolving inheritance and the default group.
     */
    fun getEffectivePermissions(uuid: String): Set<String> {
        val result = mutableSetOf<String>()
        val negated = mutableSetOf<String>()

        // Default group applies to everyone
        getDefaultGroup()?.let { collectPermissions(it, result, negated, mutableSetOf()) }

        // Player-specific groups
        val entry = players[uuid]
        if (entry != null) {
            for (groupName in entry.groups) {
                val group = getGroup(groupName) ?: continue
                collectPermissions(group, result, negated, mutableSetOf())
            }
        }

        // Remove negated permissions
        result.removeAll(negated)
        return result
    }

    /**
     * Checks if a player has a specific permission, supporting wildcards and negation.
     */
    fun hasPermission(uuid: String, permission: String): Boolean {
        val effective = getEffectivePermissions(uuid)
        return matchesPermission(effective, permission)
    }

    // ── Display (Prefix/Suffix) ──────────────────────────────────

    /**
     * Returns the prefix and suffix for a player, resolved from their highest-priority group.
     * Falls back to the default group if the player has no explicit groups.
     */
    fun getPlayerDisplay(uuid: String): PlayerDisplay {
        val entry = players[uuid]
        val playerGroups = entry?.groups?.mapNotNull { getGroup(it) } ?: emptyList()
        val defaultGroup = getDefaultGroup()

        // Collect all applicable groups (player groups + default)
        val allGroups = if (playerGroups.isEmpty() && defaultGroup != null) {
            listOf(defaultGroup)
        } else {
            playerGroups
        }

        // Pick the group with highest priority
        val bestGroup = allGroups.maxByOrNull { it.priority }
            ?: defaultGroup

        return PlayerDisplay(
            prefix = bestGroup?.prefix ?: "",
            suffix = bestGroup?.suffix ?: "",
            groupName = bestGroup?.name ?: "",
            priority = bestGroup?.priority ?: 0
        )
    }

    data class PlayerDisplay(
        val prefix: String,
        val suffix: String,
        val groupName: String,
        val priority: Int
    )

    fun updateGroupDisplay(groupName: String, prefix: String?, suffix: String?, priority: Int?) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        groups[group.name.lowercase()] = group.copy(
            prefix = prefix ?: group.prefix,
            suffix = suffix ?: group.suffix,
            priority = priority ?: group.priority
        )
        saveGroup(groups[group.name.lowercase()]!!)
    }

    // ── Internal ────────────────────────────────────────────────

    private fun collectPermissions(
        group: PermissionGroup,
        granted: MutableSet<String>,
        negated: MutableSet<String>,
        visited: MutableSet<String>
    ) {
        if (group.name.lowercase() in visited) return // prevent cycles
        visited.add(group.name.lowercase())

        // Resolve parents first (so child overrides parent)
        for (parentName in group.parents) {
            val parent = getGroup(parentName) ?: continue
            collectPermissions(parent, granted, negated, visited)
        }

        // Apply this group's permissions
        for (perm in group.permissions) {
            if (perm.startsWith("-")) {
                negated.add(perm.removePrefix("-"))
            } else {
                granted.add(perm)
            }
        }
    }

    companion object {
        /**
         * Checks if a specific permission is matched by the set of effective permissions,
         * including wildcard support (e.g., `nimbus.cloud.*` matches `nimbus.cloud.list`).
         */
        fun matchesPermission(effective: Set<String>, permission: String): Boolean {
            if (permission in effective) return true
            if ("*" in effective) return true

            // Check wildcard patterns
            val parts = permission.split(".")
            for (i in parts.indices) {
                val wildcard = parts.subList(0, i + 1).joinToString(".").removeSuffix(".${parts[i]}") + ".*"
                if (i > 0 && wildcard in effective) return true
            }

            // Also check exact wildcard at each level
            for (i in 1..parts.size) {
                val prefix = parts.subList(0, i - 1).joinToString(".")
                val wildcard = if (prefix.isEmpty()) "*" else "$prefix.*"
                if (wildcard in effective) return true
            }

            return false
        }
    }

    // ── TOML I/O ────────────────────────────────────────────────

    private fun loadGroups() {
        if (!permissionsDir.exists()) return

        permissionsDir.listDirectoryEntries("*.toml")
            .filter { it.name != "players.toml" }
            .forEach { file ->
                try {
                    val group = parseGroupToml(file.readText())
                    groups[group.name.lowercase()] = group
                } catch (e: Exception) {
                    logger.warn("Failed to load permission group from {}: {}", file.name, e.message)
                }
            }
    }

    private fun loadPlayers() {
        val file = permissionsDir.resolve("players.toml")
        if (!file.exists()) return

        try {
            val content = file.readText()
            parsePlayersToml(content).forEach { (uuid, entry) ->
                players[uuid] = entry
            }
        } catch (e: Exception) {
            logger.warn("Failed to load player permissions: {}", e.message)
        }
    }

    private fun saveGroup(group: PermissionGroup) {
        val file = permissionsDir.resolve("${group.name.lowercase()}.toml")
        file.writeText(buildGroupToml(group))
    }

    private fun savePlayers() {
        val file = permissionsDir.resolve("players.toml")
        file.writeText(buildPlayersToml())
    }

    // ── TOML Serialization ──────────────────────────────────────

    private fun buildGroupToml(group: PermissionGroup): String = buildString {
        appendLine("[group]")
        appendLine("name = ${tomlString(group.name)}")
        appendLine("default = ${group.default}")
        appendLine("prefix = ${tomlString(group.prefix)}")
        appendLine("suffix = ${tomlString(group.suffix)}")
        appendLine("priority = ${group.priority}")
        appendLine()
        appendLine("[group.permissions]")
        appendLine("list = [")
        group.permissions.forEach { appendLine("    ${tomlString(it)},") }
        appendLine("]")
        appendLine()
        appendLine("[group.inheritance]")
        appendLine("parents = [${group.parents.joinToString(", ") { tomlString(it) }}]")
    }

    private fun buildPlayersToml(): String = buildString {
        for ((uuid, entry) in players) {
            appendLine("[${tomlString(uuid)}]")
            appendLine("name = ${tomlString(entry.name)}")
            appendLine("groups = [${entry.groups.joinToString(", ") { tomlString(it) }}]")
            appendLine()
        }
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

    // ── TOML Parsing (simple hand-rolled, no ktoml dependency needed) ──

    private fun parseGroupToml(content: String): PermissionGroup {
        var name = ""
        var default = false
        var prefix = ""
        var suffix = ""
        var priority = 0
        val permissions = mutableListOf<String>()
        val parents = mutableListOf<String>()

        var inPermissionsList = false

        for (rawLine in content.lines()) {
            val line = rawLine.trim()

            // Multi-line array for permissions
            if (inPermissionsList) {
                if (line == "]") {
                    inPermissionsList = false
                    continue
                }
                val value = extractTomlString(line.removeSuffix(",").trim())
                if (value != null) permissions.add(value)
                continue
            }

            if (line.startsWith("[") || line.startsWith("#") || line.isEmpty()) continue

            val eqIndex = line.indexOf('=')
            if (eqIndex < 0) continue

            val key = line.substring(0, eqIndex).trim()
            val rawValue = line.substring(eqIndex + 1).trim()

            when (key) {
                "name" -> name = extractTomlString(rawValue) ?: rawValue
                "default" -> default = rawValue.toBooleanStrictOrNull() ?: false
                "prefix" -> prefix = extractTomlString(rawValue) ?: ""
                "suffix" -> suffix = extractTomlString(rawValue) ?: ""
                "priority" -> priority = rawValue.toIntOrNull() ?: 0
                "list" -> {
                    if (rawValue == "[") {
                        inPermissionsList = true
                    } else {
                        parseInlineArray(rawValue).forEach { permissions.add(it) }
                    }
                }
                "parents" -> parseInlineArray(rawValue).forEach { parents.add(it) }
            }
        }

        require(name.isNotBlank()) { "Permission group has no name" }
        return PermissionGroup(name, default, prefix, suffix, priority, permissions, parents)
    }

    private fun parsePlayersToml(content: String): Map<String, PlayerEntry> {
        val result = mutableMapOf<String, PlayerEntry>()
        var currentUuid: String? = null
        var currentName = ""
        var currentGroups = mutableListOf<String>()

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // Section header: ["uuid"]
            if (line.startsWith("[")) {
                // Save previous entry
                if (currentUuid != null) {
                    result[currentUuid] = PlayerEntry(currentName, currentGroups)
                }
                currentUuid = extractTomlString(line.removePrefix("[").removeSuffix("]").trim())
                    ?: line.removePrefix("[").removeSuffix("]").trim().removeSurrounding("\"")
                currentName = ""
                currentGroups = mutableListOf()
                continue
            }

            val eqIndex = line.indexOf('=')
            if (eqIndex < 0) continue

            val key = line.substring(0, eqIndex).trim()
            val rawValue = line.substring(eqIndex + 1).trim()

            when (key) {
                "name" -> currentName = extractTomlString(rawValue) ?: rawValue
                "groups" -> currentGroups = parseInlineArray(rawValue).toMutableList()
            }
        }

        // Save last entry
        if (currentUuid != null) {
            result[currentUuid] = PlayerEntry(currentName, currentGroups)
        }

        return result
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

    private fun parseInlineArray(value: String): List<String> {
        val trimmed = value.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()

        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        return inner.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { extractTomlString(it) }
    }
}
