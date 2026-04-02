package dev.kryonix.nimbus.module.refinery

import dev.kryonix.nimbus.NimbusVersion
import dev.kryonix.nimbus.event.EventBus
import dev.kryonix.nimbus.group.GroupManager
import dev.kryonix.nimbus.module.ModuleContext
import dev.kryonix.nimbus.module.NimbusModule
import dev.kryonix.nimbus.module.service
import dev.kryonix.nimbus.refinery.RefineryCommand
import dev.kryonix.nimbus.refinery.RefineryIntegration
import dev.kryonix.nimbus.service.ServiceRegistry

class RefineryModule : NimbusModule {
    override val id = "refinery"
    override val name = "Refinery"
    override val version: String get() = NimbusVersion.version
    override val description = "Game framework integration (telemetry, auto-scaling)"

    private lateinit var integration: RefineryIntegration

    override suspend fun init(context: ModuleContext) {
        val eventBus = context.service<EventBus>()!!
        val registry = context.service<ServiceRegistry>()!!
        val groupManager = context.service<GroupManager>()!!

        integration = RefineryIntegration(
            eventBus, registry, groupManager, context.scope, context.templatesDir
        )
        integration.init()

        context.registerCommand(RefineryCommand(integration, registry))
    }

    override suspend fun enable() {}

    override fun disable() {
        integration.shutdown()
    }
}
