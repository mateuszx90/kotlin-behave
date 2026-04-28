plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.google.devtools.ksp") version libs.versions.ksp.get()
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

dependencies {
    implementation(project(":annotations"))
    implementation(project(":core"))
    implementation(project(":kotest"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.runner.junit5)

    kspTest(project(":ksp"))
}

ksp {
    arg("behave.featureDir", "src/test/resources")
    arg("behave.projectDir", projectDir.absolutePath)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
