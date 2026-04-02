package dev.kryonix.nimbus.console.commands

import dev.kryonix.nimbus.console.Command
import dev.kryonix.nimbus.console.ConsoleFormatter
import dev.kryonix.nimbus.module.ModuleManager

class ModulesCommand(private val moduleManager: ModuleManager) : Command {
    override val name = "modules"
    override val description = "List loaded modules"
    override val usage = "modules"

    override suspend fun execute(args: List<String>) {
        val modules = moduleManager.getModules()
        if (modules.isEmpty()) {
            println("${ConsoleFormatter.DIM}No modules loaded.${ConsoleFormatter.RESET}")
            return
        }

        println("${ConsoleFormatter.BOLD}Loaded modules (${modules.size}):${ConsoleFormatter.RESET}")
        for (module in modules) {
            println("  ${ConsoleFormatter.CYAN}${module.name}${ConsoleFormatter.RESET} " +
                    "${ConsoleFormatter.DIM}v${module.version}${ConsoleFormatter.RESET} " +
                    "— ${module.description}")
        }
    }
}
