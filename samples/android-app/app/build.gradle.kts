import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.rizukirr.audx.samples.app"
    compileSdk = 36 // activity-compose 1.13.0 (and transitives) require >= 36

    defaultConfig {
        applicationId = "dev.rizukirr.audx.samples.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The audx-kmp jvm jar bundles desktop JNI shims as java resources;
        // Android loads its own copies from jniLibs/, so keep desktop ones out.
        resources.excludes += "natives/**"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT")

    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("io.ktor:ktor-client-cio:3.5.0")
}
