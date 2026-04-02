package dev.kryonix.nimbus.module.perms

import dev.kryonix.nimbus.NimbusVersion
import dev.kryonix.nimbus.api.routes.permissionRoutes
import dev.kryonix.nimbus.console.commands.PermsCommand
import dev.kryonix.nimbus.database.DatabaseManager
import dev.kryonix.nimbus.event.EventBus
import dev.kryonix.nimbus.module.ModuleContext
import dev.kryonix.nimbus.module.NimbusModule
import dev.kryonix.nimbus.module.service
import dev.kryonix.nimbus.permissions.PermissionManager

class PermsModule : NimbusModule {
    override val id = "perms"
    override val name = "Permissions"
    override val version: String get() = NimbusVersion.version
    override val description = "Permission groups, tracks, prefix/suffix, audit log"

    private lateinit var permissionManager: PermissionManager

    override suspend fun init(context: ModuleContext) {
        val db = context.service<DatabaseManager>()!!
        val eventBus = context.service<EventBus>()!!

        permissionManager = PermissionManager(db)
        permissionManager.init()

        context.registerCommand(PermsCommand(permissionManager, eventBus))
        context.registerRoutes({ permissionRoutes(permissionManager, eventBus) })
    }

    override suspend fun enable() {}

    override fun disable() {}
}
