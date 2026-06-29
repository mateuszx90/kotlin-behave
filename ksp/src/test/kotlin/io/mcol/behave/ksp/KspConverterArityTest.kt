package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-time validation of `@TypeConverter` arity. The generated code reads
 * `params[i + idx]` for `0 until converter.paramCount`, so a converter that wants more
 * parameters than the step captures indexes past the runtime params list. When the surplus
 * parameters are String-typed the generated cast still compiles (`params[n] as String`), so the
 * failure escapes to runtime as IndexOutOfBounds — only an arity check catches it at KSP time.
 */
class KspConverterArityTest {

    private fun nameSource() = KspTestSupport.source(
        "NameSteps.kt",
        """
        package gen.name

        import io.mcol.behave.annotations.BehaveFeature
        import io.mcol.behave.annotations.Type
        import io.mcol.behave.annotations.TypeConverter

        data class FullName(val first: String, val last: String)

        @TypeConverter
        fun toFullName(first: String, last: String): FullName = FullName(first, last)

        @BehaveFeature("name.feature", generateTest = false)
        class NameSteps {
            suspend fun theUserIs(@Type(FullName::class) name: FullName) {}
        }
        """.trimIndent(),
    )

    @Test
    fun `a converter wanting more params than the step captures fails the build`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "name.feature" to
                    """
                    Feature: Names
                        Scenario: too few captures
                            Given the user is "Alice"
                    """.trimIndent(),
            ),
            sources = listOf(nameSource()),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "toFullName")
        assertContains(outcome.messages, "IndexOutOfBounds")
    }

    @Test
    fun `a converter whose arity matches the captured params compiles`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "name.feature" to
                    """
                    Feature: Names
                        Scenario: matching captures
                            Given the user is "Alice" "Smith"
                    """.trimIndent(),
            ),
            sources = listOf(nameSource()),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        assertContains(outcome.generated("NameStepsSpec"), "toFullName(params[0] as String, params[1] as String)")
    }
}
