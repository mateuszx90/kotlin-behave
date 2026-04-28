plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

dependencies {
    implementation(project(":annotations"))
    implementation(project(":core"))
    implementation(libs.ksp.api)

    testImplementation(kotlin("test"))
}
