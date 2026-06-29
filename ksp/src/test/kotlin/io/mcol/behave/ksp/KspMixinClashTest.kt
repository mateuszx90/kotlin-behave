package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Compile-time validation that two `@StepsMixin` interfaces don't declare the same step method.
 * The processor builds a mixin registry up front and rejects a duplicate `(name, paramTypes)`
 * because a step could otherwise resolve to two competing default bodies.
 */
class KspMixinClashTest {

    @Test
    fun `the same step method in two mixins fails the build and names both interfaces`() {
        val outcome = KspTestSupport.compile(
            features = emptyMap(),
            sources = listOf(
                KspTestSupport.source(
                    "Mixins.kt",
                    """
                    package gen.clash

                    import io.mcol.behave.annotations.StepsMixin

                    @StepsMixin
                    interface AlphaMixin {
                        suspend fun iDoThing() {}
                    }

                    @StepsMixin
                    interface BetaMixin {
                        suspend fun iDoThing() {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "declared by multiple @StepsMixin interfaces")
        assertContains(outcome.messages, "AlphaMixin")
        assertContains(outcome.messages, "BetaMixin")
    }

    @Test
    fun `distinct step methods across mixins compile`() {
        val outcome = KspTestSupport.compile(
            features = emptyMap(),
            sources = listOf(
                KspTestSupport.source(
                    "Mixins.kt",
                    """
                    package gen.noclash

                    import io.mcol.behave.annotations.StepsMixin

                    @StepsMixin
                    interface AlphaMixin {
                        suspend fun iDoThing() {}
                    }

                    @StepsMixin
                    interface BetaMixin {
                        suspend fun iDoOtherThing() {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(outcome.exitCode == KotlinCompilation.ExitCode.OK, "build should pass: ${outcome.messages}")
    }
}
