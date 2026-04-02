plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":nimbus-core"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    compileOnly("io.ktor:ktor-server-core:3.1.1")
}

kotlin {
    jvmToolchain(21)
}
