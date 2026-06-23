package io.mcol.behave.gherkin

import kotlin.test.Test
import kotlin.test.assertEquals

class GherkinTableTest {
    @Test
    fun `splits cells and trims`() {
        assertEquals(listOf("a", "b", "c"), GherkinTable.splitRow("| a | b | c |"))
    }

    @Test
    fun `preserves empty cells`() {
        assertEquals(listOf("a", "", "c"), GherkinTable.splitRow("| a |  | c |"))
    }

    @Test
    fun `honours escapes for pipe backslash and newline`() {
        assertEquals(listOf("a|b", "x\\y", "l1\nl2"), GherkinTable.splitRow("""| a\|b | x\\y | l1\nl2 |"""))
    }
}
