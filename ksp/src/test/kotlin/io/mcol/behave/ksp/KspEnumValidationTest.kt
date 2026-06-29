package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-time validation of enum literals: when a step parameter is converted via
 * `@Type(SomeEnum::class)` (no custom converter), the generated code calls
 * `Enum.valueOf(value.uppercase())`. A feature literal that names no real constant would
 * throw at runtime — these tests assert the processor rejects it at KSP time instead.
 */
class KspEnumValidationTest {

    private fun colorSource() = KspTestSupport.source(
        "ColorSteps.kt",
        """
        package gen.color

        import io.mcol.behave.annotations.BehaveFeature
        import io.mcol.behave.annotations.Type

        enum class Color { RED, GREEN, BLUE }

        @BehaveFeature("color.feature", generateTest = false)
        class ColorSteps {
            suspend fun iPick(@Type(Color::class) color: Color) {}
        }
        """.trimIndent(),
    )

    @Test
    fun `a literal naming a real constant compiles`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "color.feature" to
                    """
                    Feature: Color
                        Scenario: Pick a known colour
                            When I pick "green"
                    """.trimIndent(),
            ),
            sources = listOf(colorSource()),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        // Sanity: the interface really was generated with the enum-typed param.
        assertContains(outcome.generated("ColorStepsSpec"), "suspend fun iPick(string: Color)")
    }

    @Test
    fun `an unknown enum literal fails the build and names the valid constants`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "color.feature" to
                    """
                    Feature: Color
                        Scenario: Pick a bogus colour
                            When I pick "purple"
                    """.trimIndent(),
            ),
            sources = listOf(colorSource()),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "Invalid enum value")
        assertContains(outcome.messages, "'purple'")
        assertContains(outcome.messages, "Color")
        // The message lists the real constants so the fix is obvious.
        assertContains(outcome.messages, "RED, GREEN, BLUE".split(", ").sorted().joinToString())
        // And it names the scenario where the mistake lives.
        assertContains(outcome.messages, "Pick a bogus colour")
    }

    @Test
    fun `case-insensitive match is accepted`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "color.feature" to
                    """
                    Feature: Color
                        Scenario: Mixed case still resolves
                            When I pick "Blue"
                    """.trimIndent(),
            ),
            sources = listOf(colorSource()),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
    }

    @Test
    fun `a bad value in a Scenario Outline Examples row fails the build`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "color.feature" to
                    """
                    Feature: Color
                        Scenario Outline: Pick each colour
                            When I pick "<colour>"

                            Examples:
                                | colour |
                                | red    |
                                | mauve  |
                    """.trimIndent(),
            ),
            sources = listOf(colorSource()),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "Invalid enum value")
        assertContains(outcome.messages, "'mauve'")
    }
}
