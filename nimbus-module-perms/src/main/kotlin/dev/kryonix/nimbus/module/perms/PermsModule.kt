package dev.kryonix.nimbus.module.perms

import dev.kryonix.nimbus.NimbusVersion
import dev.kryonix.nimbus.database.DatabaseManager
import dev.kryonix.nimbus.event.EventBus
import dev.kryonix.nimbus.module.ModuleContext
import dev.kryonix.nimbus.module.NimbusModule
import dev.kryonix.nimbus.module.service
import dev.kryonix.nimbus.module.perms.commands.PermsCommand
import dev.kryonix.nimbus.module.perms.routes.permissionRoutes

class PermsModule : NimbusModule {
    override val id = "perms"
    override val name = "Permissions"
    override val version: String get() = NimbusVersion.version
    override val description = "Permission groups, tracks, prefix/suffix, audit log"

    private lateinit var permissionManager: PermissionManager

    override suspend fun init(context: ModuleContext) {
        val db = context.service<DatabaseManager>()!!
        val eventBus = context.service<EventBus>()!!

        // Create permission tables if they don't exist
        db.createTables(
            PermissionGroups, GroupPermissions, GroupParents,
            Players, PlayerGroups,
            GroupMeta, PlayerMeta,
            GroupPermissionContexts, PlayerGroupContexts,
            PermissionTracks, PermissionAuditLog
        )

        permissionManager = PermissionManager(db)
        permissionManager.init()

        context.registerCommand(PermsCommand(permissionManager, eventBus))
        context.registerRoutes({ permissionRoutes(permissionManager, eventBus) })
    }

    override suspend fun enable() {}

    override fun disable() {}
}
