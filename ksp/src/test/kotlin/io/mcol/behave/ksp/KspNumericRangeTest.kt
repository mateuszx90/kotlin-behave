package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-time validation of numeric literal ranges. A standalone integer literal is always
 * typed `{int}` (Int) regardless of magnitude, so the runtime `toInt()` overflows for values
 * outside Int's range. The regex pattern (`-?\d+`) accepts them, so only a range check catches
 * it — these tests assert the processor rejects an overflowing literal at KSP time.
 */
class KspNumericRangeTest {

    private fun widgetSource() = KspTestSupport.source(
        "WidgetSteps.kt",
        """
        package gen.widget

        import io.mcol.behave.annotations.BehaveFeature

        @BehaveFeature("widget.feature", generateTest = false)
        class WidgetSteps
        """.trimIndent(),
    )

    private fun compileWith(stepLine: String) = KspTestSupport.compile(
        features = mapOf(
            "widget.feature" to
                """
                Feature: Widgets
                    Scenario: count
                        $stepLine
                """.trimIndent(),
        ),
        sources = listOf(widgetSource()),
    )

    @Test
    fun `an in-range integer literal compiles`() {
        val outcome = compileWith("Given I have 42 widgets")
        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
    }

    @Test
    fun `an integer literal above Int MAX fails the build`() {
        val outcome = compileWith("Given I have 9999999999 widgets")
        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "9999999999")
        assertContains(outcome.messages, "does not fit")
        assertContains(outcome.messages, "Int")
        // It names the scenario so the offending row is obvious.
        assertContains(outcome.messages, "count")
    }

    @Test
    fun `Int MAX exactly is still accepted`() {
        val outcome = compileWith("Given I have 2147483647 widgets")
        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
    }
}
