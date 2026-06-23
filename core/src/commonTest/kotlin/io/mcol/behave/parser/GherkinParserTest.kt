package io.mcol.behave.parser

import io.mcol.behave.model.*
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
        assertEquals(Keyword.WHEN, steps[1].keyword)
        assertEquals(Keyword.THEN, steps[2].keyword)
        assertEquals(Keyword.AND, steps[3].keyword)
        assertEquals(Keyword.BUT, steps[4].keyword)
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

    // region background -----------------------------------------------------

    @Test
    fun `parses background steps`() {
        val input = """
            Feature: F

              Background:
                Given a background step

              Scenario: S
                When a scenario step
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(1, feature.background?.steps?.size)
        assertEquals("a background step", feature.background?.steps?.get(0)?.text)
    }

    @Test
    fun `background steps are not included in scenario steps`() {
        val input = """
            Feature: F

              Background:
                Given setup

              Scenario: S
                When action
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(1, feature.scenarios[0].steps.size)
        assertEquals(Keyword.WHEN, feature.scenarios[0].steps[0].keyword)
    }

    @Test
    fun `feature without background has null background`() {
        val input = """
            Feature: F

              Scenario: S
                Given step
        """.trimIndent()
        assertEquals(null, GherkinParser.parse(input).background)
    }

    // endregion

    // region scenario outline -----------------------------------------------

    @Test
    fun `expands scenario outline into flat scenarios per row`() {
        val input = """
            Feature: F

              Scenario Outline: counts
                Given <count> words
                Then result is <expected>

                Examples:
                  | count | expected |
                  | 5     | 5        |
                  | 11    | 10       |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(2, feature.scenarios.size)
        assertEquals("counts [count=5, expected=5]", feature.scenarios[0].name)
        assertEquals("5 words", feature.scenarios[0].steps[0].text)
        assertEquals("result is 10", feature.scenarios[1].steps[1].text)
    }

    @Test
    fun `doc string is attached to the step with content preserved`() {
        val input = """
            Feature: F

              Scenario: S
                Given a payload:
                  ```
                  line1
                  # still content

                  line3
                  ```
                Then it is sent
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        val steps = feature.scenarios[0].steps
        assertEquals(2, steps.size)
        assertEquals("a payload:", steps[0].text)
        assertEquals("line1\n# still content\n\nline3", steps[0].docString)
        assertEquals(null, steps[1].docString)
    }

    @Test
    fun `doc string de-indents relative to the opening fence`() {
        val input = """
            Feature: F

              Scenario: S
                Given json:
                  ```
                  {
                    "a": 1
                  }
                  ```
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals("{\n  \"a\": 1\n}", feature.scenarios[0].steps[0].docString)
    }

    @Test
    fun `table cells honour escapes for pipe backslash and newline`() {
        val input = """
            Feature: F

              Scenario: S
                Given the following items:
                  | name | note  | multi    |
                  | a\|b | x\\y  | l1\nl2   |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        val row = feature.scenarios[0].steps[0].dataTable!!.rows[0]
        assertEquals("a|b", row["name"])
        assertEquals("x\\y", row["note"])
        assertEquals("l1\nl2", row["multi"])
    }

    @Test
    fun `empty data table cells are preserved`() {
        val input = """
            Feature: F

              Scenario: S
                Given the following items:
                  | name | qty |
                  | a    |     |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        val rows = feature.scenarios[0].steps[0].dataTable!!.rows
        assertEquals(1, rows.size)
        assertEquals("a", rows[0]["name"])
        assertEquals("", rows[0]["qty"])
    }

    @Test
    fun `asterisk is a valid step keyword`() {
        val input = """
            Feature: F

              Scenario: S
                Given a precondition
                * another precondition
                When I act
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(1, feature.scenarios.size)
        assertEquals(3, feature.scenarios[0].steps.size)
        assertEquals("another precondition", feature.scenarios[0].steps[1].text)
    }

    @Test
    fun `Example is a synonym for Scenario`() {
        val input = """
            Feature: F

              Example: a single case
                Given something
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(1, feature.scenarios.size)
        assertEquals("a single case", feature.scenarios[0].name)
        assertEquals("something", feature.scenarios[0].steps[0].text)
    }

    @Test
    fun `Scenarios is a synonym for Examples`() {
        val input = """
            Feature: F

              Scenario Outline: counts
                Given <count> words

                Scenarios:
                  | count |
                  | 5     |
                  | 11    |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(2, feature.scenarios.size)
        assertEquals("5 words", feature.scenarios[0].steps[0].text)
        assertEquals("11 words", feature.scenarios[1].steps[0].text)
    }

    @Test
    fun `Scenario Template is treated like Scenario Outline`() {
        val input = """
            Feature: F

              Scenario Template: counts
                Given <count> words
                Then result is <expected>

                Examples:
                  | count | expected |
                  | 5     | 5        |
                  | 11    | 10       |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(2, feature.scenarios.size)
        assertEquals("counts [count=5, expected=5]", feature.scenarios[0].name)
        assertEquals("5 words", feature.scenarios[0].steps[0].text)
        assertEquals("result is 10", feature.scenarios[1].steps[1].text)
    }

    @Test
    fun `outline scenario name includes row values`() {
        val input = """
            Feature: F

              Scenario Outline: my outline
                Given <x>

                Examples:
                  | x |
                  | a |
                  | b |
        """.trimIndent()
        val scenarios = GherkinParser.parse(input).scenarios
        assertEquals("my outline [x=a]", scenarios[0].name)
        assertEquals("my outline [x=b]", scenarios[1].name)
    }

    // endregion

    // region data table -----------------------------------------------------

    @Test
    fun `parses data table attached to a step`() {
        val input = """
            Feature: F

              Scenario: S
                Given the following words
                  | word   | translation |
                  | Hund   | dog         |
                  | Katze  | cat         |
        """.trimIndent()
        val step = GherkinParser.parse(input).scenarios[0].steps[0]
        assertEquals("the following words", step.text)
        val table = step.dataTable
        assertEquals(2, table?.rows?.size)
        assertEquals("Hund", table?.rows?.get(0)?.get("word"))
        assertEquals("cat", table?.rows?.get(1)?.get("translation"))
    }

    @Test
    fun `step without data table has null dataTable`() {
        val input = """
            Feature: F

              Scenario: S
                Given a plain step
        """.trimIndent()
        assertEquals(null, GherkinParser.parse(input).scenarios[0].steps[0].dataTable)
    }

    // endregion
}
