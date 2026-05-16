plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("behave.publish")
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
        // Zero runtime dependencies — SOURCE retention annotations only
    }
}
