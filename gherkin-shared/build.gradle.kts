plugins {
    alias(libs.plugins.kotlin.jvm)
    id("behave.publish")
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

// Platform-neutral Gherkin semantics shared between the KSP processor and the IntelliJ
// plugin: step→method-name generation, collision resolution, type/placeholder inference,
// normalization. Pure Kotlin — NO KSP and NO IntelliJ dependencies — so both consumers
// compute identical results from a single source of truth.
dependencies {
    testImplementation(kotlin("test"))
}
