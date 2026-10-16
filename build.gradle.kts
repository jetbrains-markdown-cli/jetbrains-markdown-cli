import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
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
        // Clikt 5.1.0 defines the same symbol in `clikt` and `clikt-mordant`; the cached debug test binary
        // fails to link with "duplicate symbol ... selfAndAncestors". Release binaries are not affected.
        target.binaries.withType<TestExecutable>().configureEach {
            @OptIn(KotlinNativeCacheApi::class)
            disableNativeCache(DisableCacheInKotlinVersion.`2_4_21`, "Clikt duplicate symbol when linking tests")
            if (target.konanTarget.family == Family.LINUX) linkerOpts("-Wl,--as-needed")
        }
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
            implementation("com.github.ajalt.clikt:clikt:5.1.0")
            implementation("it.krzeminski:snakeyaml-engine-kmp:5.0.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
