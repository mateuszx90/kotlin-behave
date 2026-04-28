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
        // kotlinx-coroutines-core is needed in jvmMain for runBlocking in SuspendBridge
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
