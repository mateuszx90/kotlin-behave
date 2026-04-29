import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

kotlin {
    jvm()
    js {
        nodejs()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    linuxX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Native host tests (linuxX64, macosX64, macosArm64) read .feature files relative to cwd.
// Embedded GeneratedFeatures (configured in root build.gradle.kts) covers the iOS simulator
// sandbox case; both paths active means tests pass whether or not GeneratedFeatures.install()
// has been called.
tasks.withType<KotlinNativeHostTest>().configureEach {
    workingDir = file("src/commonTest/resources").absolutePath
}
