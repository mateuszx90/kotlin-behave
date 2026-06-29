package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-time validation of consistent DataTable usage. The generated binding for a step has a
 * single shape: if the step is seen with a `| table |`, the binding reads `params.dataTable!!`.
 * A scenario that uses the same step WITHOUT a table then NPEs at runtime. These tests assert the
 * processor rejects that inconsistency at KSP time.
 */
class KspDataTableConsistencyTest {

    private fun itemSource() = KspTestSupport.source(
        "ItemSteps.kt",
        """
        package gen.item

        import io.mcol.behave.annotations.BehaveFeature

        @BehaveFeature("items.feature", generateTest = false)
        class ItemSteps
        """.trimIndent(),
    )

    @Test
    fun `the same step with and without a table fails the build`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "items.feature" to
                    """
                    Feature: Items
                        Scenario: seeded with a table
                            Given the following items exist
                                | name |
                                | Milk |

                        Scenario: forgot the table
                            Given the following items exist
                    """.trimIndent(),
            ),
            sources = listOf(itemSource()),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail: ${outcome.messages}")
        assertContains(outcome.messages, "Inconsistent DataTable usage")
        assertContains(outcome.messages, "the following items exist")
    }

    @Test
    fun `a step that always carries a table compiles`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "items.feature" to
                    """
                    Feature: Items
                        Scenario: one
                            Given the following items exist
                                | name |
                                | Milk |

                        Scenario: two
                            Given the following items exist
                                | name  |
                                | Bread |
                    """.trimIndent(),
            ),
            sources = listOf(itemSource()),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
    }

    @Test
    fun `a step that never carries a table compiles`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "items.feature" to
                    """
                    Feature: Items
                        Scenario: one
                            Given the following items exist

                        Scenario: two
                            Given the following items exist
                    """.trimIndent(),
            ),
            sources = listOf(itemSource()),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
    }
}
