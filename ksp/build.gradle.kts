plugins {
    alias(libs.plugins.kotlin.jvm)
    id("behave.publish")
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

dependencies {
    implementation(project(":gherkin-shared"))
    implementation(project(":annotations"))
    implementation(project(":core"))
    implementation(libs.ksp.api)
    compileOnly(gradleApi())

    testImplementation(kotlin("test"))
}
