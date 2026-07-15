package io.mcol.behave.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Extension for `io.mcol.kotlin-behave`. Override the directory feature files live in:
 *
 * ```kotlin
 * behave {
 *     featureDir = "src/test/resources"
 * }
 * ```
 */
abstract class BehaveExtension {
    /** Directory holding `.feature` files, relative to the project dir. Wired into the `behave.featureDir` KSP arg. */
    var featureDir: String = DEFAULT_FEATURE_DIR

    companion object {
        const val DEFAULT_FEATURE_DIR: String = "src/test/resources"
    }
}

/**
 * Convention plugin that collapses the whole kotlin-behave consumer setup into a single
 * `plugins { id("io.mcol.kotlin-behave") }`.
 *
 * It applies the Kotlin JVM and KSP plugins, wires the `:core` / `:kotest` / `:annotations`
 * / `:ksp` artifacts plus the kotest engine + JUnit5 runner, sets the `behave.featureDir`
 * and `behave.projectDir` KSP args, registers the feature directory as a KSP task input,
 * and switches the test task to the JUnit Platform.
 */
class KotlinBehavePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("behave", BehaveExtension::class.java)

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")

        project.dependencies.apply {
            add("testImplementation", "io.mcol.kotlin-behave:core:$BEHAVE_VERSION")
            add("testImplementation", "io.mcol.kotlin-behave:kotest:$BEHAVE_VERSION")
            add("testImplementation", "io.kotest:kotest-framework-engine:$KOTEST_VERSION")
            add("testImplementation", "io.kotest:kotest-runner-junit5:$KOTEST_VERSION")
            add("compileOnly", "io.mcol.kotlin-behave:annotations:$BEHAVE_VERSION")
            add("kspTest", "io.mcol.kotlin-behave:ksp:$BEHAVE_VERSION")
        }

        project.tasks.withType(Test::class.java).configureEach { useJUnitPlatform() }

        val lint = project.tasks.register("behaveLint", BehaveLintTask::class.java) {
            group = "verification"
            description = "Parses every .feature (failing on unparseable ones) and reports dead step definitions."

            reportFile.convention(
                project.layout.buildDirectory.file("reports/behave/lint-report.txt"),
            )

            featureDir.convention(
                project.layout.projectDirectory.dir("src/test/resources"),
            )

            stepSources.from(
                project.layout.projectDirectory.dir("src/test/kotlin"),
                project.layout.projectDirectory.dir("src/commonTest/kotlin"),
            )
        }

        project.afterEvaluate {
            val featureDirPath = extension.featureDir
            val ksp = project.extensions.getByType(KspExtension::class.java)
            ksp.arg("behave.featureDir", featureDirPath)
            ksp.arg("behave.projectDir", project.projectDir.absolutePath)

            val featureDirectory = project.layout.projectDirectory.dir(featureDirPath)
            project.tasks.matching { it.name.startsWith("ksp") }.configureEach {
                inputs.dir(featureDirectory)
            }
            lint.configure { this.featureDir.set(featureDirectory) }
        }
    }

    companion object {
        const val BEHAVE_VERSION: String = "0.1.0"
        const val KOTEST_VERSION: String = "6.1.11"
    }
}
