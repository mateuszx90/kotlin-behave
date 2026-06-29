package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Part 1 spike: prove the kctfork wiring before the full matrix.
 *
 * One `@BehaveFeature` class + a one-step `.feature` on disk → run KSP via kctfork in KSP2
 * mode → assert the compilation exits OK and the generated `*StepsSpec` contains the
 * expected suspend signatures.
 */
class KspGenerationSpikeTest {

    @Test
    fun `processor runs end-to-end and generates the spec interface`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "greeting.feature" to
                    """
                    Feature: Greeting
                        Scenario: Greet someone
                            When I greet "World"
                            Then I see a greeting
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "GreetingSteps.kt",
                    """
                    package gen.spike

                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("greeting.feature", generateTest = false)
                    class GreetingSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            outcome.exitCode,
            "KSP + downstream compile should succeed.\n${outcome.messages}",
        )
        assertTrue(outcome.hasGenerated("GreetingStepsSpec"), "spec file should be generated")

        val spec = outcome.generated("GreetingStepsSpec")
        assertContains(spec, "interface GreetingStepsSpec")
        assertContains(spec, "suspend fun iGreet(string: String)")
        assertContains(spec, "suspend fun iSeeAGreeting()")
    }
}
