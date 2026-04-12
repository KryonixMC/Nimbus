package dev.nimbuspowered.nimbus

object NimbusVersion {
    val version: String by lazy {
        NimbusVersion::class.java.`package`?.implementationVersion
            ?: readGradlePropertiesVersion()
            ?: "dev"
    }

    /** Fallback for IDE/source builds where the JAR manifest is unavailable. */
    private fun readGradlePropertiesVersion(): String? {
        return try {
            val propsFile = java.nio.file.Path.of("gradle.properties")
            if (java.nio.file.Files.exists(propsFile)) {
                java.util.Properties().apply {
                    java.nio.file.Files.newBufferedReader(propsFile).use { load(it) }
                }.getProperty("nimbusVersion")
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
