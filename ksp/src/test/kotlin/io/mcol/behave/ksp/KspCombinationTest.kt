package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * End-to-end coverage of annotation COMBINATIONS:
 * - `@BehaveType` (placeholder) together with `@BehaveCast` on another token of the same step
 * - multiple repeatable `@BehaveType` driving a DataTable Row class
 * - a `@StepsMixin` and a `@DivergentStep` coexisting in the same compilation
 */
class KspCombinationTest {

    @Test
    fun `BehaveType placeholder and BehaveCast combine in one step`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "load.feature" to
                    """
                    Feature: Load
                        Scenario: s
                            When I load 5 of {product}
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "LoadSteps.kt",
                    """
                    package gen.combo

                    import io.mcol.behave.annotations.BehaveCast
                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.BehaveType

                    data class Product(val sku: String)

                    @BehaveFeature("load.feature", generateTest = false)
                    @BehaveType(placeholder = "product", type = Product::class)
                    class LoadSteps {
                        suspend fun iLoadOf(@BehaveCast int: Int, product: Product) {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("LoadStepsSpec")
        assertContains(spec, "suspend fun iLoadOf(int: Int, product: Product)")
        assertContains(spec, "params[0] as Double") // @BehaveCast widening
        assertContains(spec, ".toInt()")
        assertContains(spec, "as gen.combo.Product") // @BehaveType placeholder (cast by FQN)
    }

    @Test
    fun `multiple repeatable BehaveType annotations drive a DataTable Row class`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "pets.feature" to
                    """
                    Feature: Pets
                        Scenario: Register
                            Given the following pets:
                                | name | breed    | age |
                                | Rex  | Shepherd | 5   |
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "PetSteps.kt",
                    """
                    package gen.pets

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.BehaveType

                    data class Pet(val name: String, val breed: String)
                    data class PetAge(val years: Int)

                    @BehaveFeature("pets.feature", generateTest = false)
                    @BehaveType(type = Pet::class)
                    @BehaveType(placeholder = "age", type = PetAge::class)
                    class PetSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("PetStepsSpec")
        // Two type mappings (no single one covering all columns) → a generated Row class.
        assertContains(spec, "data class TheFollowingPetsRow")
        assertContains(spec, "suspend fun theFollowingPets(rows: List<TheFollowingPetsRow>)")
    }

    @Test
    fun `a mixin and a divergent step coexist in the same compilation`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "a.feature" to
                    """
                    Feature: A
                        Scenario: x
                            Given the app starts
                            When I log in
                    """.trimIndent(),
                "b.feature" to
                    """
                    Feature: B
                        Scenario: y
                            Given the app starts
                            When I log in
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "MixinAndDivergent.kt",
                    """
                    package gen.mixdiv

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.DivergentStep
                    import io.mcol.behave.annotations.StepsMixin

                    @StepsMixin
                    interface InitMixin {
                        suspend fun theAppStarts() {}
                    }

                    @BehaveFeature("a.feature", generateTest = false)
                    class ASteps {
                        @DivergentStep
                        suspend fun iLogIn() {}
                    }

                    @BehaveFeature("b.feature", generateTest = false)
                    class BSteps {
                        @DivergentStep
                        suspend fun iLogIn() {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val specA = outcome.generated("AStepsSpec")
        // "the app starts" is inherited from the mixin; "I log in" diverges per feature.
        assertContains(specA, "AStepsSpec : InitMixin")
        assertContains(specA, "suspend fun iLogIn()")
        assertFalse(specA.contains("fun theAppStarts"), "mixin step must not be redeclared:\n$specA")
    }
}
