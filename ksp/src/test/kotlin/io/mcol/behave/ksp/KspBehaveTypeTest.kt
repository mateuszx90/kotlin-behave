package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * End-to-end coverage of `@BehaveType` in all three modes (placeholder, field auto-detect,
 * field explicit) plus DataTable mapping (a user type covering all columns vs an unmapped
 * table that produces an auto-generated Row class).
 */
class KspBehaveTypeTest {

    @Test
    fun `placeholder mode maps a single token to a domain type`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "tap.feature" to
                    """
                    Feature: Tap
                        Scenario: Navigate
                            When I tap {label}
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "TapSteps.kt",
                    """
                    package gen.tap

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.BehaveType

                    data class ButtonLabel(val value: String)

                    @BehaveFeature("tap.feature", generateTest = false)
                    @BehaveType(placeholder = "label", type = ButtonLabel::class)
                    class TapSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        assertContains(outcome.generated("TapStepsSpec"), "suspend fun iTap(label: ButtonLabel)")
    }

    @Test
    fun `field auto-detect groups constructor-matching tokens into one parameter`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "move.feature" to
                    """
                    Feature: Move
                        Scenario: Coordinates
                            When I move to {x} {y}
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "MoveSteps.kt",
                    """
                    package gen.move

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.BehaveType

                    data class Point(val x: Int, val y: Int)

                    @BehaveFeature("move.feature", generateTest = false)
                    @BehaveType(type = Point::class)
                    class MoveSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        // {x} and {y} (matching Point's ctor params) collapse into a single Point parameter.
        assertContains(outcome.generated("MoveStepsSpec"), "suspend fun iMoveTo(point: Point)")
    }

    @Test
    fun `field explicit groups the listed tokens into one parameter`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "size.feature" to
                    """
                    Feature: Size
                        Scenario: Dimensions
                            When the size is {w} by {h}
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "SizeSteps.kt",
                    """
                    package gen.size

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.BehaveType

                    data class Rect(val width: Int, val height: Int)

                    @BehaveFeature("size.feature", generateTest = false)
                    @BehaveType(type = Rect::class, fields = ["w", "h"])
                    class SizeSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        assertContains(outcome.generated("SizeStepsSpec"), "suspend fun theSizeIsBy(rect: Rect)")
    }

    @Test
    fun `datatable maps to user type when it covers all columns and a Row class otherwise`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "vocab.feature" to
                    """
                    Feature: Vocab
                        Scenario: Load words
                            Given the following vocabulary:
                                | polish | english |
                                | pies   | dog     |

                        Scenario: Load inventory
                            Given the following inventory:
                                | product | quantity |
                                | Apple   | 50       |
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "VocabSteps.kt",
                    """
                    package gen.vocab

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.BehaveType

                    data class WordRow(val polish: String, val english: String)

                    @BehaveFeature("vocab.feature", generateTest = false)
                    @BehaveType(type = WordRow::class)
                    class VocabSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("VocabStepsSpec")
        // WordRow covers every column → the step takes List<WordRow> directly (no Row class).
        assertContains(spec, "suspend fun theFollowingVocabulary(rows: List<WordRow>)")
        // The unmapped inventory table → an auto-generated all-String Row class.
        assertContains(spec, "data class TheFollowingInventoryRow")
        assertContains(spec, "suspend fun theFollowingInventory(rows: List<TheFollowingInventoryRow>)")
    }
}
