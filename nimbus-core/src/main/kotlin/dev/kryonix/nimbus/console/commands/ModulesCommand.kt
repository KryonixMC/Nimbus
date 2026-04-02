package dev.kryonix.nimbus.console.commands

import dev.kryonix.nimbus.console.Command
import dev.kryonix.nimbus.console.ConsoleFormatter
import dev.kryonix.nimbus.console.ConsoleFormatter.BOLD
import dev.kryonix.nimbus.console.ConsoleFormatter.CYAN
import dev.kryonix.nimbus.console.ConsoleFormatter.DIM
import dev.kryonix.nimbus.console.ConsoleFormatter.GREEN
import dev.kryonix.nimbus.console.ConsoleFormatter.RED
import dev.kryonix.nimbus.console.ConsoleFormatter.RESET
import dev.kryonix.nimbus.console.ConsoleFormatter.YELLOW
import dev.kryonix.nimbus.module.ModuleInfo
import dev.kryonix.nimbus.module.ModuleManager
import org.jline.terminal.Terminal
import org.jline.utils.NonBlockingReader

class ModulesCommand(
    private val moduleManager: ModuleManager,
    private val terminal: Terminal
) : Command {
    override val name = "modules"
    override val description = "Manage controller modules"
    override val usage = "modules [list|install|uninstall <id>]"

    override suspend fun execute(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase() ?: "list"
        when (sub) {
            "list", "ls" -> list()
            "install", "add" -> {
                val id = args.getOrNull(1)
                if (id != null) {
                    installDirect(id.lowercase())
                } else {
                    installInteractive()
                }
            }
            "uninstall", "remove" -> {
                val id = args.getOrNull(1)
                if (id == null) {
                    println(ConsoleFormatter.error("Usage: modules uninstall <module-id>"))
                    return
                }
                uninstall(id.lowercase())
            }
            else -> {
                println(ConsoleFormatter.error("Unknown subcommand: $sub"))
                println("${DIM}Usage: $usage$RESET")
            }
        }
    }

    private fun list() {
        val loaded = moduleManager.getModules()
        val available = moduleManager.discoverAvailable()
        val loadedIds = loaded.map { it.id }.toSet()

        println("${BOLD}Modules:$RESET")
        println()

        if (loaded.isNotEmpty()) {
            for (module in loaded) {
                println("  ${GREEN}●$RESET ${CYAN}${module.name}$RESET ${DIM}v${module.version}$RESET — ${module.description}")
            }
        }

        // Show available but not installed modules
        val notInstalled = available.filter { it.id !in loadedIds }
        if (notInstalled.isNotEmpty()) {
            if (loaded.isNotEmpty()) println()
            for (mod in notInstalled) {
                println("  ${DIM}○ ${mod.name}$RESET ${DIM}— ${mod.description}$RESET")
            }
            println()
            println("  ${DIM}Install with: ${CYAN}modules install$RESET")
        }

        if (loaded.isEmpty() && notInstalled.isEmpty()) {
            println("  ${DIM}No modules found.$RESET")
        }
    }

    // ── Interactive picker ──────────────────────────────────

    private fun installInteractive() {
        val available = moduleManager.discoverAvailable()
        val loadedIds = moduleManager.getModules().map { it.id }.toSet()
        val notInstalled = available.filter { it.id !in loadedIds }

        if (notInstalled.isEmpty()) {
            println("${DIM}All available modules are already installed.$RESET")
            return
        }

        val selected = mutableSetOf<String>()
        var cursor = 0
        val w = terminal.writer()

        // Save terminal into raw mode for key reading
        val originalAttrs = terminal.enterRawMode()
        val reader = terminal.reader()

        try {
            // Hide cursor
            w.print("\u001B[?25l")
            w.flush()

            // Draw initial picker
            drawPicker(w, notInstalled, selected, cursor)

            while (true) {
                val key = readKey(reader)
                when (key) {
                    Key.UP -> cursor = (cursor - 1 + notInstalled.size) % notInstalled.size
                    Key.DOWN -> cursor = (cursor + 1) % notInstalled.size
                    Key.SPACE -> {
                        val id = notInstalled[cursor].id
                        if (id in selected) selected.remove(id) else selected.add(id)
                    }
                    Key.ENTER -> break
                    Key.ESCAPE, Key.CTRL_C -> {
                        // Clear and abort
                        clearPicker(w, notInstalled.size)
                        w.print("\u001B[?25h") // show cursor
                        w.flush()
                        println("${DIM}Cancelled.$RESET")
                        return
                    }
                    else -> {}
                }

                // Redraw
                clearPicker(w, notInstalled.size)
                drawPicker(w, notInstalled, selected, cursor)
            }

            // Clear picker and show cursor
            clearPicker(w, notInstalled.size)
            w.print("\u001B[?25h")
            w.flush()
        } finally {
            terminal.setAttributes(originalAttrs)
            // Ensure cursor is visible even on error
            w.print("\u001B[?25h")
            w.flush()
        }

        // Install selected modules
        if (selected.isEmpty()) {
            println("${DIM}No modules selected.$RESET")
            return
        }

        var installed = 0
        for (id in selected) {
            val result = moduleManager.install(id)
            if (result == ModuleManager.InstallResult.INSTALLED) {
                val info = available.find { it.id == id }
                println("  ${GREEN}●$RESET Installed ${CYAN}${info?.name ?: id}$RESET")
                installed++
            }
        }
        if (installed > 0) {
            println()
            println("  ${YELLOW}Restart Nimbus to activate ${if (installed == 1) "the module" else "$installed modules"}.$RESET")
        }
    }

    private fun drawPicker(w: java.io.Writer, modules: List<ModuleInfo>, selected: Set<String>, cursor: Int) {
        w.write("  ${BOLD}Select modules to install:$RESET ${DIM}(↑↓ navigate, space toggle, enter confirm)$RESET\n")
        for ((i, mod) in modules.withIndex()) {
            val isSelected = mod.id in selected
            val isCursor = i == cursor
            val checkbox = if (isSelected) "${GREEN}✓$RESET" else "${DIM}○$RESET"
            val pointer = if (isCursor) "${CYAN}›$RESET " else "  "
            val nameColor = if (isCursor) CYAN else ""
            val nameReset = if (isCursor) RESET else ""
            w.write("  $pointer$checkbox $nameColor${mod.name}$nameReset ${DIM}— ${mod.description}$RESET\n")
        }
        w.flush()
    }

    private fun clearPicker(w: java.io.Writer, itemCount: Int) {
        // Move cursor up (items + header) and clear each line
        val lines = itemCount + 1
        for (i in 0 until lines) {
            w.write("\u001B[A") // up
        }
        for (i in 0 until lines) {
            w.write("\u001B[2K") // clear line
            if (i < lines - 1) w.write("\u001B[B") // down
        }
        // Move back to top
        for (i in 0 until lines - 1) {
            w.write("\u001B[A")
        }
        w.write("\r")
        w.flush()
    }

    private enum class Key { UP, DOWN, SPACE, ENTER, ESCAPE, CTRL_C, OTHER }

    private fun readKey(reader: NonBlockingReader): Key {
        val c = reader.read()
        return when (c) {
            13, 10 -> Key.ENTER
            32 -> Key.SPACE
            27 -> {
                // Escape sequence or standalone ESC
                val next = reader.peek(50)
                if (next == -2 || next == -1) return Key.ESCAPE
                reader.read() // consume '['
                when (reader.read()) {
                    65 -> Key.UP    // ESC[A
                    66 -> Key.DOWN  // ESC[B
                    else -> Key.OTHER
                }
            }
            3 -> Key.CTRL_C
            else -> Key.OTHER
        }
    }

    // ── Direct install/uninstall ────────────────────────────

    private fun installDirect(id: String) {
        val result = moduleManager.install(id)
        when (result) {
            ModuleManager.InstallResult.INSTALLED -> {
                val info = moduleManager.discoverAvailable().find { it.id == id }
                println("${GREEN}●$RESET Installed ${CYAN}${info?.name ?: id}$RESET")
                println("  ${YELLOW}Restart Nimbus to activate the module.$RESET")
            }
            ModuleManager.InstallResult.ALREADY_INSTALLED -> {
                println("${DIM}Module '$id' is already installed.$RESET")
            }
            ModuleManager.InstallResult.NOT_FOUND -> {
                println(ConsoleFormatter.error("Module '$id' not found."))
                val available = moduleManager.discoverAvailable()
                if (available.isNotEmpty()) {
                    println("  ${DIM}Available: ${available.joinToString(", ") { it.id }}$RESET")
                }
            }
        }
    }

    private fun uninstall(id: String) {
        if (!moduleManager.uninstall(id)) {
            println(ConsoleFormatter.error("Module '$id' is not installed."))
            return
        }
        println("${RED}●$RESET Uninstalled ${CYAN}$id$RESET")
        if (moduleManager.isLoaded(id)) {
            println("  ${YELLOW}Module is still active until restart.$RESET")
        }
    }
}
