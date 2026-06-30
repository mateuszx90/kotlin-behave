package io.mcol.behave.gherkin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueValidationTest {

    private enum class Color { RED, GREEN, BLUE }

    // region enums

    @Test
    fun `enumProblem accepts a constant case-insensitively`() {
        val constants = listOf("RED", "GREEN", "BLUE")
        assertNull(ValueValidation.enumProblem("red", "Color", constants))
        assertNull(ValueValidation.enumProblem("Blue", "Color", constants))
    }

    @Test
    fun `enumProblem rejects an unknown constant and lists the valid ones sorted`() {
        val message = assertNotNull(ValueValidation.enumProblem("purple", "Color", listOf("RED", "GREEN", "BLUE")))
        assertTrue("'purple'" in message)
        assertTrue("Color" in message)
        assertTrue("BLUE, GREEN, RED" in message, "constants must be listed sorted: $message")
    }

    @Test
    fun `toEnum resolves case-insensitively`() {
        assertEquals(Color.RED, ValueValidation.toEnum("red", Color.values(), "Color"))
        assertEquals(Color.BLUE, ValueValidation.toEnum("BLUE", Color.values(), "Color"))
    }

    @Test
    fun `toEnum throws the shared message for an unknown value`() {
        val ex = assertFailsWith<IllegalArgumentException> { ValueValidation.toEnum("purple", Color.values(), "Color") }
        val message = assertNotNull(ex.message)
        assertTrue("'purple'" in message)
        assertTrue("BLUE, GREEN, RED" in message)
    }

    // endregion

    // region Duration

    @Test
    fun `durationProblem accepts a parseable literal`() {
        assertNull(ValueValidation.durationProblem("1500ms"))
        assertNull(ValueValidation.durationProblem("2s"))
    }

    @Test
    fun `durationProblem rejects an unparseable literal`() {
        val message = assertNotNull(ValueValidation.durationProblem("soon"))
        assertTrue("'soon'" in message)
    }

    @Test
    fun `toDuration parses or throws the shared message`() {
        assertEquals(ValueValidation.toDuration("1500ms"), ValueValidation.toDuration("1500ms"))
        val ex = assertFailsWith<IllegalArgumentException> { ValueValidation.toDuration("soon") }
        assertTrue("'soon'" in assertNotNull(ex.message))
    }

    // endregion

    // region numeric range

    @Test
    fun `numericRangeProblem flags Int overflow but accepts in-range and the boundary`() {
        assertNull(ValueValidation.numericRangeProblem("42", "Int"))
        assertNull(ValueValidation.numericRangeProblem("2147483647", "int"))
        val message = assertNotNull(ValueValidation.numericRangeProblem("9999999999", "Int"))
        assertTrue("9999999999" in message && "Int" in message)
    }

    @Test
    fun `numericRangeProblem flags Long overflow`() {
        assertNull(ValueValidation.numericRangeProblem("9999999999", "Long"))
        assertNotNull(ValueValidation.numericRangeProblem("99999999999999999999", "long"))
    }

    @Test
    fun `numericRangeProblem ignores non-integer types`() {
        assertNull(ValueValidation.numericRangeProblem("anything", "Double"))
        assertNull(ValueValidation.numericRangeProblem("anything", "String"))
    }

    // endregion
}
