package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * End-to-end coverage of `@BehaveCast`: a step auto-detected as `{int}` whose scenarios also
 * carry a decimal value. The cast widens `{int}` → `{double}` in the step expression and
 * generates a truncating `.toInt()` conversion (instead of a compile-time type mismatch).
 */
class KspBehaveCastTest {

    @Test
    fun `BehaveCast widens int to double and truncates`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "portions.feature" to
                    """
                    Feature: Portions
                        Scenario: Whole portions
                            When I create a recipe with 4 portions

                        Scenario: Decimal portions
                            When I create a recipe with 2.5 portions
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "PortionSteps.kt",
                    """
                    package gen.portions

                    import io.mcol.behave.annotations.BehaveCast
                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("portions.feature", generateTest = false)
                    class PortionSteps {
                        suspend fun iCreateARecipeWithPortions(@BehaveCast int: Int) {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("PortionStepsSpec")
        // The abstract signature still exposes the narrow Int type to the implementer.
        assertContains(spec, "suspend fun iCreateARecipeWithPortions(int: Int)")
        // Step expression widened so the decimal scenario matches at runtime.
        assertContains(spec, "I create a recipe with {double} portions")
        // Received as Double, truncated to Int at the call site.
        assertContains(spec, "params[0] as Double")
        assertContains(spec, ".toInt()")
    }
}
