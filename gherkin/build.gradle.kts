plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("behave.publish")
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

// Platform-neutral Gherkin semantics shared across ALL consumers: the runtime parser (:core),
// the KSP processor (:ksp) and the IntelliJ plugin. Pure Kotlin — NO KSP and NO IntelliJ
// dependencies — so every consumer computes identical step→method names and parameter types
// from a single source of truth. Targets mirror :core so it can be used from its commonMain.
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
        }
    }
}
