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
    fun `Rule background is added to rule scenarios on top of the feature background`() {
        val input = """
            Feature: F

              Background:
                Given feature setup

              Rule: first rule
                Background:
                  Given rule setup

                Scenario: S1
                  When act
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        // Feature-level background stays separate (the runner prepends it to every scenario).
        assertEquals(listOf("feature setup"), feature.background!!.steps.map { it.text })
        assertEquals(1, feature.scenarios.size)
        assertEquals("S1", feature.scenarios[0].name)
        // The rule's own background is baked into the rule scenario's steps, before its steps.
        assertEquals(listOf("rule setup", "act"), feature.scenarios[0].steps.map { it.text })
    }

    @Test
    fun `Rule without its own background leaves scenarios unchanged`() {
        val input = """
            Feature: F

              Rule: r
                Scenario: S
                  When act
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(1, feature.scenarios.size)
        assertEquals(listOf("act"), feature.scenarios[0].steps.map { it.text })
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
    fun `table cells unescape pipe and backslash but keep backslash-n literal`() {
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
        assertEquals("l1\\nl2", row["multi"])
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

    // region multiple Examples blocks ---------------------------------------

    @Test
    fun `scenario outline supports multiple Examples blocks`() {
        val input = """
            Feature: F

              Scenario Outline: counts
                Given <count> words
                Then result is <expected>

                Examples:
                  | count | expected |
                  | 5     | 5        |

                Examples:
                  | count | expected |
                  | 11    | 10       |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(2, feature.scenarios.size)
        assertEquals("5 words", feature.scenarios[0].steps[0].text)
        assertEquals("11 words", feature.scenarios[1].steps[0].text)
        assertEquals("result is 10", feature.scenarios[1].steps[1].text)
    }

    @Test
    fun `each Examples block carries its own tags merged with feature and scenario tags`() {
        val input = """
            @feat
            Feature: F

              @outline
              Scenario Outline: counts
                Given <count> words

                @smoke
                Examples:
                  | count |
                  | 1     |

                @regression
                Examples:
                  | count |
                  | 2     |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(setOf("@feat", "@outline", "@smoke"), feature.scenarios[0].tags)
        assertEquals(setOf("@feat", "@outline", "@regression"), feature.scenarios[1].tags)
    }

    // endregion

    // region outline substitution into doc strings & data tables -------------

    @Test
    fun `outline substitutes variables inside doc strings`() {
        val input = """
            Feature: F

              Scenario Outline: payloads
                When I send:
                  ```
                  name=<name>
                  ```

                Examples:
                  | name  |
                  | alice |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals("name=alice", feature.scenarios[0].steps[0].docString)
    }

    @Test
    fun `outline substitutes variables inside data table cells and headers`() {
        val input = """
            Feature: F

              Scenario Outline: configs
                Given the config:
                  | <key> | value  |
                  | name  | <name> |

                Examples:
                  | key  | name  |
                  | attr | alice |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        val row = feature.scenarios[0].steps[0].dataTable!!.rows[0]
        assertEquals("alice", row["value"])
        assertEquals("name", row["attr"])
    }

    // endregion

    // region Rule tags -------------------------------------------------------

    @Test
    fun `scenarios inherit Rule tags merged with feature and scenario tags`() {
        val input = """
            @feat
            Feature: F

              @rule
              Rule: r
                @s
                Scenario: S
                  When act
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(setOf("@feat", "@rule", "@s"), feature.scenarios[0].tags)
    }

    @Test
    fun `a second Rule replaces the first Rule's tags`() {
        val input = """
            Feature: F

              @r1
              Rule: one
                Scenario: A
                  When act

              @r2
              Rule: two
                Scenario: B
                  When act
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(setOf("@r1"), feature.scenarios[0].tags)
        assertEquals(setOf("@r2"), feature.scenarios[1].tags)
    }

    // endregion

    // region i18n / localized keywords ---------------------------------------

    @Test
    fun `parses a German feature via the language header`() {
        val input = """
            # language: de
            Funktionalität: Zähler

              Grundlage:
                Angenommen ein Zähler bei 0

              Szenario: Hochzählen
                Wenn ich erhöhe
                Und ich erhöhe
                Dann ist es 2
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals("Zähler", feature.name)
        // Background "Angenommen" -> Given; its keyword and text both survive translation.
        assertEquals(Keyword.GIVEN, feature.background!!.steps[0].keyword)
        assertEquals(listOf("ein Zähler bei 0"), feature.background!!.steps.map { it.text })
        assertEquals(1, feature.scenarios.size)
        assertEquals("Hochzählen", feature.scenarios[0].name)
        // Background steps are not part of scenario.steps (the runner prepends them), so the
        // scenario holds only When/And/Then.
        val steps = feature.scenarios[0].steps
        assertEquals(3, steps.size)
        assertEquals(Keyword.WHEN, steps[0].keyword)
        assertEquals(Keyword.AND, steps[1].keyword)
        assertEquals(Keyword.THEN, steps[2].keyword)
        // Step text is NOT translated — only keywords are.
        assertEquals("ich erhöhe", steps[0].text)
    }

    @Test
    fun `expands a localized Scenario Outline`() {
        val input = """
            # language: de
            Funktionalität: F

              Szenariogrundriss: zählen
                Wenn ich <n> nehme
                Dann ist es <n>

                Beispiele:
                  | n |
                  | 3 |
                  | 5 |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(2, feature.scenarios.size)
        assertEquals("ich 3 nehme", feature.scenarios[0].steps[0].text)
        assertEquals("ich 5 nehme", feature.scenarios[1].steps[0].text)
    }

    @Test
    fun `localized keyword inside a doc string is left untranslated`() {
        val input = """
            # language: de
            Funktionalität: F

              Szenario: S
                Wenn ich sende:
                  ```
                  Wenn this German keyword stays literal
                  ```
                Dann ist es gesendet
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        // The "Wenn " line inside the doc string must remain content, not become "When ".
        assertEquals("Wenn this German keyword stays literal", feature.scenarios[0].steps[0].docString)
    }

    @Test
    fun `an English feature is unaffected by i18n translation`() {
        val input = """
            Feature: Plain

              Scenario: S
                Given a thing
                When I act
                Then it works
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals("Plain", feature.name)
        assertEquals(3, feature.scenarios[0].steps.size)
        assertEquals(Keyword.GIVEN, feature.scenarios[0].steps[0].keyword)
    }

    // endregion

    // region doc string content type -----------------------------------------

    @Test
    fun `doc string captures content type after the backtick fence`() {
        val input = """
            Feature: F

              Scenario: S
                Given json:
                  ```json
                  {"a": 1}
                  ```
        """.trimIndent()
        val step = GherkinParser.parse(input).scenarios[0].steps[0]
        assertEquals("json", step.docStringContentType)
        assertEquals("{\"a\": 1}", step.docString)
    }

    @Test
    fun `doc string captures content type after the quote fence`() {
        val input = "" +
            "Feature: F\n" +
            "\n" +
            "  Scenario: S\n" +
            "    Given xml:\n" +
            "      \"\"\"xml\n" +
            "      <tag/>\n" +
            "      \"\"\"\n"
        val step = GherkinParser.parse(input).scenarios[0].steps[0]
        assertEquals("xml", step.docStringContentType)
        assertEquals("<tag/>", step.docString)
    }

    @Test
    fun `doc string without content type leaves it null`() {
        val input = """
            Feature: F

              Scenario: S
                Given plain:
                  ```
                  hello
                  ```
        """.trimIndent()
        val step = GherkinParser.parse(input).scenarios[0].steps[0]
        assertEquals(null, step.docStringContentType)
        assertEquals("hello", step.docString)
    }

    // endregion
}
