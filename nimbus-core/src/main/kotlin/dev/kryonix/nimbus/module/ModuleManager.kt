package dev.kryonix.nimbus.module

import org.slf4j.LoggerFactory
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.jar.JarFile
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Discovers, loads, and manages [NimbusModule] instances from JAR files.
 *
 * Modules are loaded from the `modules/` directory using [ServiceLoader].
 * Each JAR must declare its module implementation in
 * `META-INF/services/dev.kryonix.nimbus.module.NimbusModule`.
 */
class ModuleManager(
    private val modulesDir: Path,
    private val context: ModuleContext
) {

    private val logger = LoggerFactory.getLogger(ModuleManager::class.java)
    private val modules = linkedMapOf<String, NimbusModule>()
    private val classLoaders = mutableListOf<URLClassLoader>()

    /** Load all module JARs from [modulesDir]. */
    fun loadAll() {
        if (!modulesDir.exists() || !modulesDir.isDirectory()) {
            logger.debug("Modules directory does not exist: {}", modulesDir)
            return
        }

        val jars = modulesDir.listDirectoryEntries("*.jar")
        if (jars.isEmpty()) {
            logger.info("No modules found in {}", modulesDir)
            return
        }

        logger.info("Found {} module JAR(s) in {}", jars.size, modulesDir)

        for (jar in jars) {
            try {
                val classLoader = URLClassLoader(
                    arrayOf(jar.toUri().toURL()),
                    this::class.java.classLoader
                )
                classLoaders.add(classLoader)

                val loader = ServiceLoader.load(NimbusModule::class.java, classLoader)
                for (module in loader) {
                    if (modules.containsKey(module.id)) {
                        logger.warn("Duplicate module id '{}' from {} — skipping", module.id, jar.fileName)
                        continue
                    }
                    modules[module.id] = module
                    logger.info("Loaded module: {} v{} ({})", module.name, module.version, module.id)
                }
            } catch (e: Exception) {
                logger.error("Failed to load module from {}: {}", jar.fileName, e.message, e)
            }
        }
    }

    /** Initialize and enable all loaded modules. */
    suspend fun enableAll() {
        for ((id, module) in modules) {
            try {
                module.init(context)
                logger.info("Initialized module: {}", module.name)
            } catch (e: Exception) {
                logger.error("Failed to initialize module '{}': {}", id, e.message, e)
            }
        }
        for ((id, module) in modules) {
            try {
                module.enable()
            } catch (e: Exception) {
                logger.error("Failed to enable module '{}': {}", id, e.message, e)
            }
        }
    }

    /** Disable all modules (in reverse order) and close class loaders. */
    fun disableAll() {
        for ((id, module) in modules.entries.reversed()) {
            try {
                module.disable()
                logger.info("Disabled module: {}", module.name)
            } catch (e: Exception) {
                logger.error("Failed to disable module '{}': {}", id, e.message, e)
            }
        }
        for (cl in classLoaders) {
            try {
                cl.close()
            } catch (_: Exception) {}
        }
        classLoaders.clear()
    }

    fun getModule(id: String): NimbusModule? = modules[id]
    fun getModules(): List<NimbusModule> = modules.values.toList()
    fun isLoaded(id: String): Boolean = modules.containsKey(id)

    companion object {
        /**
         * Reads module metadata from a JAR's `module.properties` resource
         * without loading the module class. Used by the SetupWizard.
         */
        fun readModuleProperties(jarPath: Path): ModuleInfo? {
            return try {
                JarFile(jarPath.toFile()).use { jar ->
                    val entry = jar.getEntry("module.properties") ?: return null
                    val props = Properties()
                    jar.getInputStream(entry).use { props.load(it) }
                    ModuleInfo(
                        id = props.getProperty("id") ?: return null,
                        name = props.getProperty("name") ?: return null,
                        description = props.getProperty("description") ?: "",
                        defaultEnabled = props.getProperty("default")?.toBoolean() ?: false,
                        fileName = jarPath.fileName.toString()
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Lightweight module descriptor read from `module.properties` (no class loading required). */
data class ModuleInfo(
    val id: String,
    val name: String,
    val description: String,
    val defaultEnabled: Boolean,
    val fileName: String
)
