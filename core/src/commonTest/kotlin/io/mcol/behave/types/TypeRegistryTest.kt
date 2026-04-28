package io.mcol.behave.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TypeRegistryTest {
    // region expression compilation -----------------------------------------

    @Test
    fun `compiles expression with no placeholders to literal regex`() {
        val compiled = TypeRegistry().compile("I have words")
        assertNotNull(compiled.regex.matchEntire("I have words"))
        assertNull(compiled.regex.matchEntire("I have cats"))
    }

    @Test
    fun `compiles int placeholder`() {
        val compiled = TypeRegistry().compile("I have {int} words")
        val match = compiled.regex.matchEntire("I have 5 words")
        assertNotNull(match)
        assertEquals(5, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles int placeholder with negative value`() {
        val compiled = TypeRegistry().compile("offset is {int}")
        val match = compiled.regex.matchEntire("offset is -3")
        assertNotNull(match)
        assertEquals(-3, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles long placeholder`() {
        val compiled = TypeRegistry().compile("id is {long}")
        val match = compiled.regex.matchEntire("id is 9999999999")
        assertNotNull(match)
        assertEquals(9999999999L, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles float placeholder`() {
        val compiled = TypeRegistry().compile("ratio is {float}")
        val match = compiled.regex.matchEntire("ratio is 3.14")
        assertNotNull(match)
        assertEquals(3.14f, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles string placeholder and strips quotes`() {
        val compiled = TypeRegistry().compile("language is {string}")
        val match = compiled.regex.matchEntire("""language is "German"""")
        assertNotNull(match)
        assertEquals("German", compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles word placeholder`() {
        val compiled = TypeRegistry().compile("status is {word}")
        val match = compiled.regex.matchEntire("status is active")
        assertNotNull(match)
        assertEquals("active", compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles multiple placeholders`() {
        val compiled = TypeRegistry().compile("have {int} words and {string} lang")
        val match = compiled.regex.matchEntire("""have 5 words and "en" lang""")
        assertNotNull(match)
        val values = compiled.convert(match!!)
        assertEquals(5, values[0])
        assertEquals("en", values[1])
    }

    @Test
    fun `compiles string before int — converters follow text order not definition order`() {
        val compiled = TypeRegistry().compile("the collection {string} has {int} words")
        val match = compiled.regex.matchEntire("""the collection "Animals" has 2 words""")
        assertNotNull(match)
        val values = compiled.convert(match!!)
        assertEquals("Animals", values[0])
        assertEquals(2, values[1])
    }

    @Test
    fun `compiles string before double — converters follow text order`() {
        val compiled = TypeRegistry().compile("item {string} costs {double}")
        val match = compiled.regex.matchEntire("""item "Apple" costs 1.99""")
        assertNotNull(match)
        val values = compiled.convert(match!!)
        assertEquals("Apple", values[0])
        assertEquals(1.99, values[1])
    }

    @Test
    fun `compiles word between two ints — converters follow text order`() {
        val compiled = TypeRegistry().compile("{int} {word} items cost {int} dollars")
        val match = compiled.regex.matchEntire("3 fancy items cost 15 dollars")
        assertNotNull(match)
        val values = compiled.convert(match!!)
        assertEquals(3, values[0])
        assertEquals("fancy", values[1])
        assertEquals(15, values[2])
    }

    @Test
    fun `raw regex capture groups return String`() {
        val compiled = TypeRegistry().compile("""have (\d+) words""")
        val match = compiled.regex.matchEntire("have 5 words")
        assertNotNull(match)
        assertEquals("5", compiled.convert(match!!)[0])
    }

    // endregion
}
