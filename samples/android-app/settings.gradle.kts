pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal() // audx-kmp 0.1.0-SNAPSHOT is published locally
        google()
        mavenCentral()
    }
}

rootProject.name = "audx-android-sample"
include(":app")
