package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-time validation of `@Type(Duration::class)` literals. The generated code parses the
 * value via `kotlin.time.Duration.parse(...)`, which throws at runtime for an unparseable string.
 * The literal is known at KSP time, so these tests assert the processor rejects it up front —
 * symmetric with the enum-literal check.
 */
class KspDurationLiteralTest {

    private fun timeoutSource() = KspTestSupport.source(
        "TimeoutSteps.kt",
        """
        package gen.timeout

        import kotlin.time.Duration
        import io.mcol.behave.annotations.BehaveFeature
        import io.mcol.behave.annotations.Type

        @BehaveFeature("timeout.feature", generateTest = false)
        class TimeoutSteps {
            suspend fun itTimesOutAfter(@Type(Duration::class) d: Duration) {}
        }
        """.trimIndent(),
    )

    private fun compileWith(literal: String) = KspTestSupport.compile(
        features = mapOf(
            "timeout.feature" to
                """
                Feature: Timeouts
                    Scenario: configure
                        When it times out after "$literal"
                """.trimIndent(),
        ),
        sources = listOf(timeoutSource()),
    )

    @Test
    fun `a parseable Duration literal compiles`() {
        val outcome = compileWith("1500ms")
        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
    }

    @Test
    fun `an unparseable Duration literal fails the build`() {
        val outcome = compileWith("soon")
        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "Invalid Duration literal")
        assertContains(outcome.messages, "'soon'")
        assertContains(outcome.messages, "configure")
    }
}
