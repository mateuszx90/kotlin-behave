package io.mcol.behave.gherkin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GherkinTypesTest {
    @Test
    fun `all-numeric values infer Int`() {
        val types = GherkinTypes.inferVariableTypes(
            "I tick <n> times",
            listOf("I tick 1 times", "I tick 2 times", "I tick 3 times"),
        )
        assertEquals("Int", types["n"])
    }

    @Test
    fun `boolean values infer Boolean`() {
        val types = GherkinTypes.inferVariableTypes(
            "the premium flag is <flag>",
            listOf("the premium flag is true", "the premium flag is false"),
        )
        assertEquals("Boolean", types["flag"])
    }

    @Test
    fun `decimal values infer Double`() {
        val types = GherkinTypes.inferVariableTypes(
            "the ratio is <r>",
            listOf("the ratio is 1.5", "the ratio is 2.25"),
        )
        assertEquals("Double", types["r"])
    }

    @Test
    fun `values beyond Int range infer Long`() {
        val types = GherkinTypes.inferVariableTypes(
            "the id is <id>",
            listOf("the id is 10000000000", "the id is 20000000000"),
        )
        assertEquals("Long", types["id"])
    }

    @Test
    fun `word values stay String and are absent from the map`() {
        val types = GherkinTypes.inferVariableTypes(
            "the label is <label>",
            listOf("the label is alpha", "the label is beta"),
        )
        assertFalse("label" in types)
    }

    @Test
    fun `mixed value kinds fall back to String`() {
        val types = GherkinTypes.inferVariableTypes(
            "I submit code <code>",
            listOf("I submit code 1", "I submit code abc", "I submit code 3"),
        )
        assertFalse("code" in types)
    }

    @Test
    fun `multi-column step resolves each variable independently`() {
        val types = GherkinTypes.inferVariableTypes(
            "I record count <count> ratio <ratio> enabled <enabled> big <big> label <label>",
            listOf(
                "I record count 1 ratio 1.5 enabled true big 10000000000 label alpha",
                "I record count 2 ratio 2.25 enabled false big 20000000000 label beta",
            ),
        )
        assertEquals("Int", types["count"])
        assertEquals("Double", types["ratio"])
        assertEquals("Boolean", types["enabled"])
        assertEquals("Long", types["big"])
        assertFalse("label" in types)
    }

    @Test
    fun `table and standalone instances unify to one type`() {
        // Same step appears both as an outline column (1,2,3) and a standalone literal (5).
        val types = GherkinTypes.inferVariableTypes(
            "I tick <n> times",
            listOf("I tick 1 times", "I tick 2 times", "I tick 5 times"),
        )
        assertEquals("Int", types["n"])
    }

    @Test
    fun `quoted variable is never typed`() {
        val types = GherkinTypes.inferVariableTypes(
            "the code is \"<code>\"",
            listOf("the code is \"1\"", "the code is \"2\""),
        )
        assertFalse("code" in types)
    }

    @Test
    fun `inferType returns the unified type or null for value lists`() {
        assertEquals("Int", GherkinTypes.inferType(listOf("1", "2", "3")))
        assertEquals("Boolean", GherkinTypes.inferType(listOf("true", "false")))
        assertEquals("Double", GherkinTypes.inferType(listOf("1.5", "2.0")))
        assertEquals("Long", GherkinTypes.inferType(listOf("10000000000")))
        assertEquals(null, GherkinTypes.inferType(listOf("alpha", "beta")))
        assertEquals(null, GherkinTypes.inferType(listOf("1", "abc")))
        assertEquals(null, GherkinTypes.inferType(emptyList()))
    }

    @Test
    fun `placeholder and kotlin type maps are inverse for the canonical names`() {
        for ((kotlin, placeholder) in GherkinTypes.kotlinToPlaceholder) {
            assertEquals(kotlin, GherkinTypes.placeholderToKotlin[placeholder])
        }
        assertTrue("String" == GherkinTypes.placeholderToKotlin["word"])
    }
}
