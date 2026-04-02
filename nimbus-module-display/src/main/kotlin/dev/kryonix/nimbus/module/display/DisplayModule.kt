package dev.kryonix.nimbus.module.display

import dev.kryonix.nimbus.NimbusVersion
import dev.kryonix.nimbus.api.routes.displayRoutes
import dev.kryonix.nimbus.display.DisplayManager
import dev.kryonix.nimbus.group.GroupManager
import dev.kryonix.nimbus.module.ModuleContext
import dev.kryonix.nimbus.module.NimbusModule
import dev.kryonix.nimbus.module.service

class DisplayModule : NimbusModule {
    override val id = "display"
    override val name = "Display"
    override val version: String get() = NimbusVersion.version
    override val description = "Server selector signs + NPCs via FancyNpcs"

    private lateinit var displayManager: DisplayManager

    override suspend fun init(context: ModuleContext) {
        val groupManager = context.service<GroupManager>()!!

        val configDir = context.moduleConfigDir("display")
        displayManager = DisplayManager(configDir)
        displayManager.init()

        // Auto-generate display configs for existing groups
        val groupConfigs = groupManager.getAllGroups().map { it.config }
        displayManager.ensureDisplays(groupConfigs)

        context.registerRoutes({ displayRoutes(displayManager) })
    }

    override suspend fun enable() {}

    override fun disable() {}
}
