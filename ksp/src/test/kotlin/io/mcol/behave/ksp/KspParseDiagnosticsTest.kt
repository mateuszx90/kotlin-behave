package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Gherkin parse errors surface through KSP: the processor logs every [FeatureFileParser] error
 * and aborts the class. These tests assert each parse diagnostic fails the build with an
 * actionable message — including the Scenario Outline `<var>` ↔ Examples-column consistency check
 * (a step value that would otherwise substitute a literal `<var>` at runtime).
 */
class KspParseDiagnosticsTest {

    private fun compile(feature: String) = KspTestSupport.compile(
        features = mapOf("f.feature" to feature),
        sources = listOf(
            KspTestSupport.source(
                "FSteps.kt",
                """
                package gen.parse

                import io.mcol.behave.annotations.BehaveFeature

                @BehaveFeature("f.feature", generateTest = false)
                class FSteps
                """.trimIndent(),
            ),
        ),
    )

    @Test
    fun `a missing Feature declaration fails the build`() {
        val outcome = compile(
            """
            Scenario: orphaned
                Given something happens
            """.trimIndent(),
        )
        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "Missing Feature: declaration")
    }

    @Test
    fun `a step outside any scenario fails the build`() {
        val outcome = compile(
            """
            Feature: F
                Given an orphaned step
            """.trimIndent(),
        )
        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "found outside any Scenario or Background")
    }

    @Test
    fun `a Scenario Outline with no Examples block fails the build`() {
        val outcome = compile(
            """
            Feature: F
                Scenario Outline: O
                    Given I use <thing>
            """.trimIndent(),
        )
        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "has no Examples")
    }

    @Test
    fun `a Scenario Outline variable with no Examples column fails the build`() {
        val outcome = compile(
            """
            Feature: F
                Scenario Outline: O
                    Given I use <thing>

                    Examples:
                        | other |
                        | 1     |
            """.trimIndent(),
        )
        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "missing columns for")
        assertContains(outcome.messages, "<thing>")
    }
}
