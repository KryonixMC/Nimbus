plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":nimbus-core"))
    compileOnly("io.ktor:ktor-server-core:3.1.1")
}

kotlin {
    jvmToolchain(21)
}
