package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * End-to-end coverage of the diagnostics the processor emits. These assert that KSP FAILS
 * the build (exit != OK) and surfaces an actionable message — exercising the real failure
 * paths, not just the happy path.
 */
class KspDiagnosticsTest {

    @Test
    fun `same step in two features without a mixin or DivergentStep errors and names the classes`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "a.feature" to
                    """
                    Feature: A
                        Scenario: x
                            Given the user signs in
                    """.trimIndent(),
                "b.feature" to
                    """
                    Feature: B
                        Scenario: y
                            Given the user signs in
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "Dupes.kt",
                    """
                    package gen.dup

                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("a.feature", generateTest = false)
                    class AlphaSteps

                    @BehaveFeature("b.feature", generateTest = false)
                    class BetaSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail")
        assertContains(outcome.messages, "is declared in this feature and")
        assertContains(outcome.messages, "AlphaSteps")
        assertContains(outcome.messages, "BetaSteps")
        // The message points users at the two ways to resolve the duplication.
        assertContains(outcome.messages, "@DivergentStep")
    }

    @Test
    fun `BehaveType auto-detect on a zero-ctor-param type errors`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "x.feature" to
                    """
                    Feature: X
                        Scenario: s
                            Given something happens
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "XSteps.kt",
                    """
                    package gen.badtype

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.BehaveType

                    class Empty

                    @BehaveFeature("x.feature", generateTest = false)
                    @BehaveType(type = Empty::class)
                    class XSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail")
        assertContains(outcome.messages, "zero primary constructor parameters")
        assertContains(outcome.messages, "Empty")
    }
}
