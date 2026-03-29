package dev.nimbus

object NimbusVersion {
    val version: String by lazy {
        NimbusVersion::class.java.`package`?.implementationVersion ?: "dev"
    }
}
