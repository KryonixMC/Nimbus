package dev.nimbus.console.commands

import dev.nimbus.console.Command
import dev.nimbus.console.ConsoleFormatter
import dev.nimbus.group.GroupManager
import dev.nimbus.service.ServiceRegistry

class InfoCommand(
    private val groupManager: GroupManager,
    private val registry: ServiceRegistry
) : Command {

    override val name = "info"
    override val description = "Show detailed group configuration"
    override val usage = "info <group>"

    override suspend fun execute(args: List<String>) {
        if (args.isEmpty()) {
            println(ConsoleFormatter.error("Usage: $usage"))
            return
        }

        val groupName = args[0]
        val group = groupManager.getGroup(groupName)
        if (group == null) {
            println(ConsoleFormatter.error("Group '$groupName' not found."))
            return
        }

        val def = group.config.group
        val services = registry.getByGroup(groupName)
        val totalPlayers = services.sumOf { it.playerCount }

        println(ConsoleFormatter.header("Group: ${group.name}"))

        fun field(label: String, value: String) {
            val padded = label.padEnd(22)
            println("  ${ConsoleFormatter.colorize(padded, ConsoleFormatter.DIM)}$value")
        }

        field("Type", ConsoleFormatter.colorize(def.type.name, ConsoleFormatter.CYAN))
        field("Software", ConsoleFormatter.colorize(def.software.name, ConsoleFormatter.CYAN))
        field("Version", def.version)
        field("Template", def.template.ifEmpty { ConsoleFormatter.colorize("(default)", ConsoleFormatter.DIM) })

        println(ConsoleFormatter.section("Resources"))
        field("Memory", def.resources.memory)
        field("Max Players", def.resources.maxPlayers.toString())

        println(ConsoleFormatter.section("Scaling"))
        field("Min Instances", def.scaling.minInstances.toString())
        field("Max Instances", def.scaling.maxInstances.toString())
        field("Players/Instance", def.scaling.playersPerInstance.toString())
        field("Scale Threshold", "${(def.scaling.scaleThreshold * 100).toInt()}%")
        if (def.scaling.idleTimeout > 0) {
            field("Idle Timeout", "${def.scaling.idleTimeout}ms")
        }

        println(ConsoleFormatter.section("Lifecycle"))
        field("Stop on Empty", if (def.lifecycle.stopOnEmpty) ConsoleFormatter.success("yes") else ConsoleFormatter.colorize("no", ConsoleFormatter.DIM))
        field("Restart on Crash", if (def.lifecycle.restartOnCrash) ConsoleFormatter.success("yes") else ConsoleFormatter.colorize("no", ConsoleFormatter.DIM))
        field("Max Restarts", def.lifecycle.maxRestarts.toString())

        println(ConsoleFormatter.section("JVM Args"))
        if (def.jvm.args.isEmpty()) {
            println("  ${ConsoleFormatter.colorize("(none)", ConsoleFormatter.DIM)}")
        } else {
            for (arg in def.jvm.args) {
                println("  ${ConsoleFormatter.colorize(arg, ConsoleFormatter.DIM)}")
            }
        }

        println(ConsoleFormatter.section("Runtime"))
        field("Running Instances", if (services.isNotEmpty()) ConsoleFormatter.success(services.size.toString()) else ConsoleFormatter.colorize("0", ConsoleFormatter.DIM))
        field("Total Players", if (totalPlayers > 0) ConsoleFormatter.colorize("$totalPlayers", ConsoleFormatter.BOLD) else ConsoleFormatter.colorize("0", ConsoleFormatter.DIM))
    }
}
