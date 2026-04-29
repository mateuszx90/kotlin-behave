import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
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
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotest.framework.engine)
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.framework.engine)
            }
        }
    }
}

// Native host tests still use runtime resource reading with cwd-relative paths.
// Generated features (configured in root build.gradle.kts) cover the iOS simulator sandbox
// case; leaving both paths active means tests keep passing whether or not
// GeneratedFeatures.install() has been called.
tasks.withType<KotlinNativeHostTest>().configureEach {
    workingDir = file("src/commonTest/resources").absolutePath
}
