package io.mcol.behave.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Functional test proving a module builds with nothing but
 * `plugins { id("io.mcol.kotlin-behave") }` — KSP gets wired and the test task exists.
 */
class KotlinBehavePluginTest {
    private val projectDir = File("build/functionalTest").apply {
        deleteRecursively()
        mkdirs()
    }

    @AfterTest
    fun cleanup() {
        projectDir.deleteRecursively()
    }

    private fun write(path: String, text: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(text)
    }

    @Test
    fun `a module wires KSP and the test task from a single plugin id`() {
        write("settings.gradle.kts", """rootProject.name = "consumer"""")
        write(
            "build.gradle.kts",
            """
            plugins {
                id("io.mcol.kotlin-behave")
            }

            repositories {
                mavenCentral()
            }

            behave {
                featureDir = "src/test/resources"
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all", "--stacktrace")
            .build()

        // KSP got applied: its test-compilation task is present.
        assertContains(result.output, "kspTestKotlin")
        // Kotlin JVM got applied: its compile task is present.
        assertContains(result.output, "compileTestKotlin")
        // The behaveLint task is registered.
        assertContains(result.output, "behaveLint")
        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Expected the consumer project to configure successfully:\n${result.output}",
        )
    }
}
