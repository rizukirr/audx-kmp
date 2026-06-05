plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}

group = "dev.rizukirr.audx.samples"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.rizukirr.audx.samples.server.ApplicationKt")
}

dependencies {
    implementation("dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT")

    implementation("io.ktor:ktor-server-netty:3.5.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
    implementation("io.ktor:ktor-server-call-logging:3.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.5.0")
}
