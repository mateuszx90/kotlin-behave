package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Real-KSP verification that a step's text generates the expected `*StepsSpec` method signature
 * across parameter counts — the interface side the IDE rename refactor relies on. Renaming a step
 * rewrites the feature text and the step method to match; KSP regenerates the interface from that
 * text, and this pins what it produces (0, 1 and 2 parameters; quoted literals and numbers).
 */
class KspStepSignatureTest {

    private fun specFor(step: String): String {
        val outcome = KspTestSupport.compile(
            features = mapOf("sig.feature" to "Feature: F\n    Scenario: S\n        $step\n"),
            sources = listOf(
                KspTestSupport.source(
                    "SigSteps.kt",
                    """
                    package gen.sig

                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("sig.feature", generateTest = false)
                    class SigSteps
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        return outcome.generated("SigStepsSpec")
    }

    @Test
    fun `a step with no arguments generates a no-parameter method`() {
        assertContains(specFor("Given I am logged in"), "suspend fun iAmLoggedIn()")
    }

    @Test
    fun `a quoted argument generates one String parameter`() {
        assertContains(specFor("""When I choose "red""""), "suspend fun iChoose(string: String)")
    }

    @Test
    fun `a numeric argument generates an Int parameter`() {
        assertContains(specFor("Then I wait 5 seconds"), "suspend fun iWaitSeconds(int: Int)")
    }

    @Test
    fun `two quoted arguments generate two String parameters`() {
        assertContains(specFor("""Given I set "a" and "b""""), "suspend fun iSetAnd(string0: String, string1: String)")
    }
}
