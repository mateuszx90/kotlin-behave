package io.github.mcol.gherkin.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TypeRegistryTest {

    // region expression compilation -----------------------------------------

    @Test
    fun `compiles expression with no placeholders to literal regex`() {
        val compiled = TypeRegistry.compile("I have words")
        assertNotNull(compiled.regex.matchEntire("I have words"))
        assertNull(compiled.regex.matchEntire("I have cats"))
    }

    @Test
    fun `compiles {int} placeholder`() {
        val compiled = TypeRegistry.compile("I have {int} words")
        val match = compiled.regex.matchEntire("I have 5 words")
        assertNotNull(match)
        assertEquals(5, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles {int} with negative value`() {
        val compiled = TypeRegistry.compile("offset is {int}")
        val match = compiled.regex.matchEntire("offset is -3")
        assertNotNull(match)
        assertEquals(-3, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles {long} placeholder`() {
        val compiled = TypeRegistry.compile("id is {long}")
        val match = compiled.regex.matchEntire("id is 9999999999")
        assertNotNull(match)
        assertEquals(9999999999L, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles {float} placeholder`() {
        val compiled = TypeRegistry.compile("ratio is {float}")
        val match = compiled.regex.matchEntire("ratio is 3.14")
        assertNotNull(match)
        assertEquals(3.14f, compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles {string} placeholder and strips quotes`() {
        val compiled = TypeRegistry.compile("language is {string}")
        val match = compiled.regex.matchEntire("""language is "German"""")
        assertNotNull(match)
        assertEquals("German", compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles {word} placeholder`() {
        val compiled = TypeRegistry.compile("status is {word}")
        val match = compiled.regex.matchEntire("status is active")
        assertNotNull(match)
        assertEquals("active", compiled.convert(match!!)[0])
    }

    @Test
    fun `compiles multiple placeholders`() {
        val compiled = TypeRegistry.compile("have {int} words and {string} lang")
        val match = compiled.regex.matchEntire("""have 5 words and "en" lang""")
        assertNotNull(match)
        val values = compiled.convert(match!!)
        assertEquals(5, values[0])
        assertEquals("en", values[1])
    }

    @Test
    fun `raw regex capture groups return String`() {
        val compiled = TypeRegistry.compile("""have (\d+) words""")
        val match = compiled.regex.matchEntire("have 5 words")
        assertNotNull(match)
        assertEquals("5", compiled.convert(match!!)[0])
    }

    // endregion
}
