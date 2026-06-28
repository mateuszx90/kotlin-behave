plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("behave.publish")
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("kotlinBehave") {
            id = "io.mcol.kotlin-behave"
            implementationClass = "io.mcol.behave.gradle.KotlinBehavePlugin"
            displayName = "kotlin-behave convention plugin"
            description = "Wires KSP, the kotest engine, behave KSP args and JUnit-platform test setup in one plugin."
        }
    }
}
