plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":nimbus-core"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

kotlin {
    jvmToolchain(21)
}
