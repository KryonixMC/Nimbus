package dev.nimbuspowered.nimbus.system

import com.sun.management.OperatingSystemMXBean
import dev.nimbuspowered.nimbus.api.SystemInfoResponse
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path

/**
 * Collects host-system specs and live CPU/memory telemetry for the process that is running it.
 *
 * Static fields (OS, CPU model, core count) are cached on first read — they don't change while
 * the JVM is alive. Dynamic fields (cpuLoad, memoryUsed) are sampled on every call.
 */
object SystemInfoCollector {

    private val cachedHostname: String by lazy {
        try {
            InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME") ?: "unknown"
        }
    }

    private val cachedCpuModel: String by lazy { readCpuModel() }

    fun collect(): SystemInfoResponse {
        val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
        val totalMem = osBean?.totalMemorySize ?: 0
        val freeMem = osBean?.freeMemorySize ?: 0
        val usedMem = (totalMem - freeMem).coerceAtLeast(0)

        return SystemInfoResponse(
            hostname = cachedHostname,
            osName = System.getProperty("os.name", "unknown"),
            osVersion = System.getProperty("os.version", ""),
            osArch = System.getProperty("os.arch", ""),
            cpuModel = cachedCpuModel,
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            systemCpuLoad = osBean?.cpuLoad ?: -1.0,
            processCpuLoad = osBean?.processCpuLoad ?: -1.0,
            systemMemoryUsedMb = usedMem / 1024 / 1024,
            systemMemoryTotalMb = totalMem / 1024 / 1024,
            javaVersion = System.getProperty("java.version", ""),
            javaVendor = System.getProperty("java.vendor", "")
        )
    }

    private fun readCpuModel(): String {
        // Linux — /proc/cpuinfo "model name" line
        val cpuInfo = Path.of("/proc/cpuinfo")
        if (Files.exists(cpuInfo)) {
            try {
                val line = Files.lines(cpuInfo).use { stream ->
                    stream.filter { it.startsWith("model name") }.findFirst().orElse(null)
                }
                if (line != null) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) return parts[1].trim()
                }
            } catch (_: Exception) { /* fall through */ }
        }
        // Fallback — best-effort, same on Windows/macOS
        return System.getenv("PROCESSOR_IDENTIFIER")
            ?: System.getProperty("os.arch", "unknown")
    }
}
