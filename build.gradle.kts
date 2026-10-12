import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

plugins {
    kotlin("multiplatform") version "2.4.21"
}

group = "de.blaimi"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Only the targets for which JetBrains/markdown publishes Kotlin/Native artifacts.
val nativeTargets: List<KotlinNativeTarget> = listOf(
    kotlin.linuxX64(),
    kotlin.linuxArm64(),
    kotlin.mingwX64(),
    kotlin.macosX64(),
    kotlin.macosArm64(),
)

kotlin {
    nativeTargets.forEach { target ->
        target.binaries.executable {
            baseName = "jetbrains-markdown-cli"
            entryPoint = "main"
            if (target.konanTarget.family == Family.LINUX) {
                // The Kotlin/Native sysroot would otherwise add libcrypt.so.1 & co. as hard
                // dependencies, which are missing on many modern distros and are never used.
                linkerOpts("-Wl,--as-needed")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains:markdown:0.7.16")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
