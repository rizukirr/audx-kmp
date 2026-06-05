pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal() // audx-kmp 0.1.0-SNAPSHOT is published locally
        mavenCentral()
    }
}

rootProject.name = "audx-server-sample"
