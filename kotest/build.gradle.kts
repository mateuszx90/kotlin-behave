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
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotest.framework.engine)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.framework.engine)
        }
    }
}
