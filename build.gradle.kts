import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
    `maven-publish`
}
group = "dev.rizukirr"
version = "0.1.0-SNAPSHOT"

kotlin {
    fun KotlinNativeTarget.audxCinterop(libDir: String){
        compilations.getByName("main").cinterops {
            val audx by creating {
                defFile(project.file("src/nativeInterop/cinterop/audx.def"))
                includeDirs(project.file("native/include"))
                extraOpts("-libraryPath", project.file(libDir).absolutePath)
            }
        }
    }
    
    linuxX64 {
        audxCinterop("native/libs/linux_x64")
        binaries {
            executable {
                entryPoint = "dev.rizukirr.audx.main"
            }
        }
    }
    androidNativeArm64 { audxCinterop("native/libs/android_arm64") }
    androidNativeX64 { audxCinterop("native/libs/android_x64") }
    mingwX64 { audxCinterop("native/libs/mingw_x64") }
    jvm {}

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
