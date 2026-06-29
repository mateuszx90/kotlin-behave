package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * End-to-end coverage of `@StepsMixin` (shared step bodies) and `@DivergentStep`
 * (intentionally different bodies for the same step text across features).
 */
class KspMixinDivergentTest {

    @Test
    fun `a step shared across features via a mixin is inherited, not redeclared`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "a.feature" to
                    """
                    Feature: A
                        Scenario: x
                            Given I am initialised
                            When I do a thing
                    """.trimIndent(),
                "b.feature" to
                    """
                    Feature: B
                        Scenario: y
                            Given I am initialised
                            When I do another thing
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "Mixin.kt",
                    """
                    package gen.mixin

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.StepsMixin

                    @StepsMixin
                    interface SharedMixin {
                        suspend fun iAmInitialised() {}
                    }

                    @BehaveFeature("a.feature", generateTest = false)
                    class ASteps

                    @BehaveFeature("b.feature", generateTest = false)
                    class BSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val specA = outcome.generated("AStepsSpec")
        // Spec extends the mixin; the shared step is inherited (NOT redeclared) but the
        // feature-unique step is still abstract.
        assertContains(specA, "AStepsSpec : SharedMixin")
        assertContains(specA, "suspend fun iDoAThing()")
        assertFalse(specA.contains("fun iAmInitialised"), "shared step must not be redeclared:\n$specA")
    }

    @Test
    fun `same step text in two features compiles when both mark it DivergentStep`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "web.feature" to
                    """
                    Feature: Web
                        Scenario: x
                            Given the user is logged in
                            When I open the web app
                    """.trimIndent(),
                "mobile.feature" to
                    """
                    Feature: Mobile
                        Scenario: y
                            Given the user is logged in
                            When I open the mobile app
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "Divergent.kt",
                    """
                    package gen.divergent

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.DivergentStep

                    @BehaveFeature("web.feature", generateTest = false)
                    class WebSteps {
                        @DivergentStep
                        suspend fun theUserIsLoggedIn() {}
                    }

                    @BehaveFeature("mobile.feature", generateTest = false)
                    class MobileSteps {
                        @DivergentStep
                        suspend fun theUserIsLoggedIn() {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        assertContains(outcome.generated("WebStepsSpec"), "suspend fun theUserIsLoggedIn()")
        assertContains(outcome.generated("MobileStepsSpec"), "suspend fun theUserIsLoggedIn()")
    }
}
