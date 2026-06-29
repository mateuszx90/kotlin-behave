package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * End-to-end coverage of `@Type` + `@TypeConverter`: enum `valueOf`, a multi-parameter
 * converter consuming several tokens, and a single-parameter custom converter. Each asserts
 * the generated call site (FQN + positional args) and the converted signature type.
 */
class KspTypeConversionTest {

    @Test
    fun `enum Type generates a valueOf conversion`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "color.feature" to
                    """
                    Feature: Color
                        Scenario: Pick
                            When I pick "red"
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
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
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("ColorStepsSpec")
        // The parameter keeps its token-derived name ("string"); only its type is converted.
        assertContains(spec, "suspend fun iPick(string: Color)")
        // Enums (no @TypeConverter) resolve case-insensitively via the shared runtime validator.
        assertContains(spec, "io.mcol.behave.types.ValueValidation.toEnum(params[0] as String, gen.color.Color.values(), \"Color\")")
    }

    @Test
    fun `multi-parameter converter consumes several tokens at one call site`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "board.feature" to
                    """
                    Feature: Board
                        Scenario: Setup
                            Given I have a board of 3 x 4
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "BoardSteps.kt",
                    """
                    package gen.board

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.Type
                    import io.mcol.behave.annotations.TypeConverter

                    data class Size(val width: Int, val height: Int)

                    @TypeConverter
                    fun convertToSize(width: Int, height: Int): Size = Size(width, height)

                    @BehaveFeature("board.feature", generateTest = false)
                    class BoardSteps {
                        suspend fun iHaveABoardOfX(@Type(Size::class) size: Size) {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("BoardStepsSpec")
        // The two {int} tokens collapse into a single Size parameter (named after the first token).
        assertContains(spec, "suspend fun iHaveABoardOfX(int0: Size)")
        // Call site uses the converter by FQN, passing the two ints positionally.
        assertContains(spec, "convertToSize(params[0] as Int, params[1] as Int)")
    }

    @Test
    fun `single-parameter custom converter is called by FQN`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "wallet.feature" to
                    """
                    Feature: Wallet
                        Scenario: Balance
                            Then my balance is "USD5"
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "WalletSteps.kt",
                    """
                    package gen.wallet

                    import io.mcol.behave.annotations.BehaveFeature
                    import io.mcol.behave.annotations.Type
                    import io.mcol.behave.annotations.TypeConverter

                    data class Money(val cents: Int)

                    @TypeConverter
                    fun parseMoney(raw: String): Money = Money(raw.removePrefix("USD").toInt() * 100)

                    @BehaveFeature("wallet.feature", generateTest = false)
                    class WalletSteps {
                        suspend fun myBalanceIs(@Type(Money::class) money: Money) {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("WalletStepsSpec")
        assertContains(spec, "suspend fun myBalanceIs(string: Money)")
        assertContains(spec, "parseMoney(params[0] as String)")
    }
}
