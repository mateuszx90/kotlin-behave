plugins {
    alias(libs.plugins.kotlin.jvm)
    id("behave.publish")
}

group = "io.mcol.kotlin-behave"
version = "0.1.0"

dependencies {
    implementation(project(":gherkin"))
    implementation(project(":annotations"))
    implementation(project(":core"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    compileOnly(gradleApi())

    testImplementation(kotlin("test"))

    // End-to-end KSP generation tests: run the real processor over inline sources +
    // on-disk feature files and assert on the generated output.
    testImplementation(libs.kctfork.ksp)
    // The generated *StepsSpec / *GherkinTest reference these at compile time, so they must
    // be on the test compilation classpath (pulled in via inheritClassPath) for the e2e
    // harness to reach a clean OK exit code rather than a downstream "unresolved reference".
    testImplementation(project(":kotest"))
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotlinx.coroutines.core)
}

// kctfork's KSP API is annotated @ExperimentalCompilerApi — opt in for the test sources.
tasks
    .withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
    .matching { it.name.contains("Test") }
    .configureEach {
        compilerOptions.optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }

// kctfork 0.7.1 ships against Kotlin 2.1.10 / KSP 2.1.10. This project targets Kotlin
// 2.3.10 / KSP 2.3.9, and the processor under test is compiled against the KSP 2.3.9 API,
// so the in-process compiler/KSP that kctfork launches must match. Force every Kotlin and
// KSP artifact on the TEST classpaths to the project versions; kctfork's own thin wrapper
// API is stable across these minors. Scoped to test configurations so the published
// processor artifact is unaffected.
configurations
    .matching { it.name.startsWith("test") }
    .configureEach {
        resolutionStrategy.eachDependency {
            when {
                // Leave kapt artifacts at kctfork's native version: 2.3.x relocated the
                // `kotlin.kapt3.base.KaptOptions` classes that kctfork references unconditionally,
                // and we never run kapt (KSP2 only), so the older jars stay loadable.
                requested.group == "org.jetbrains.kotlin" &&
                    requested.name.startsWith("kotlin-annotation-processing") -> Unit
                requested.group == "org.jetbrains.kotlin" -> useVersion(libs.versions.kotlin.get())
                requested.group == "com.google.devtools.ksp" -> useVersion(libs.versions.ksp.get())
            }
        }
    }
