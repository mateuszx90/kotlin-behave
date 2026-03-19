package io.github.mcol.gherkin.parser

import io.github.mcol.gherkin.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class GherkinParserTest {

    @Test
    fun `model data classes hold values correctly`() {
        val step = Step(Keyword.GIVEN, "I have 5 words")
        val scenario = Scenario("basic", listOf(step))
        val feature = Feature("My feature", scenarios = listOf(scenario))

        assertEquals("My feature", feature.name)
        assertEquals(1, feature.scenarios.size)
        assertEquals(Keyword.GIVEN, feature.scenarios[0].steps[0].keyword)
        assertEquals("I have 5 words", feature.scenarios[0].steps[0].text)
    }

    // region basic parsing ---------------------------------------------------

    @Test
    fun `parses feature name`() {
        val input = """
            Feature: Word list adapter
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals("Word list adapter", feature.name)
    }

    @Test
    fun `parses single scenario with given step`() {
        val input = """
            Feature: Words

              Scenario: basic
                Given I have 5 words
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(1, feature.scenarios.size)
        assertEquals("basic", feature.scenarios[0].name)
        assertEquals(1, feature.scenarios[0].steps.size)
        assertEquals(Keyword.GIVEN, feature.scenarios[0].steps[0].keyword)
        assertEquals("I have 5 words", feature.scenarios[0].steps[0].text)
    }

    @Test
    fun `parses all step keywords`() {
        val input = """
            Feature: F

              Scenario: steps
                Given a given step
                When a when step
                Then a then step
                And an and step
                But a but step
        """.trimIndent()
        val steps = GherkinParser.parse(input).scenarios[0].steps
        assertEquals(Keyword.GIVEN, steps[0].keyword)
        assertEquals(Keyword.WHEN,  steps[1].keyword)
        assertEquals(Keyword.THEN,  steps[2].keyword)
        assertEquals(Keyword.AND,   steps[3].keyword)
        assertEquals(Keyword.BUT,   steps[4].keyword)
    }

    @Test
    fun `parses multiple scenarios`() {
        val input = """
            Feature: F

              Scenario: first
                Given step one

              Scenario: second
                When step two
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(2, feature.scenarios.size)
        assertEquals("first", feature.scenarios[0].name)
        assertEquals("second", feature.scenarios[1].name)
    }

    @Test
    fun `strips leading and trailing whitespace from step text`() {
        val input = """
            Feature: F

              Scenario: S
                Given   lots of spaces
        """.trimIndent()
        assertEquals("lots of spaces", GherkinParser.parse(input).scenarios[0].steps[0].text)
    }

    @Test
    fun `ignores comment lines`() {
        val input = """
            # top comment
            Feature: F

              # scenario comment
              Scenario: S
                Given step
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals("F", feature.name)
        assertEquals(1, feature.scenarios[0].steps.size)
    }

    // endregion
}
