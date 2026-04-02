plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":nimbus-core"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
}

kotlin {
    jvmToolchain(21)
}
