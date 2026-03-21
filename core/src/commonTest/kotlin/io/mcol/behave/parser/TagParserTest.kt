package io.mcol.behave.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagParserTest {

    @Test
    fun `parses feature tags`() {
        val input = """
            @smoke @auth
            Feature: Login
              Scenario: S
                Given step
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(setOf("@smoke", "@auth"), feature.tags)
    }

    @Test
    fun `parses scenario tags`() {
        val input = """
            Feature: F
              @happy-path
              Scenario: S
                Given step
        """.trimIndent()
        assertEquals(setOf("@happy-path"), GherkinParser.parse(input).scenarios[0].tags)
    }

    @Test
    fun `feature tags are inherited by all scenarios`() {
        val input = """
            @smoke
            Feature: F
              Scenario: first
                Given step
              @wip
              Scenario: second
                Given step
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertTrue("@smoke" in feature.scenarios[0].tags)
        assertTrue("@smoke" in feature.scenarios[1].tags)
        assertTrue("@wip" in feature.scenarios[1].tags)
    }

    @Test
    fun `scenario outline tags merged with examples tags`() {
        val input = """
            Feature: F
              @slow
              Scenario Outline: counts
                Given <n> words
                @admin
                Examples:
                  | n |
                  | 5 |
        """.trimIndent()
        val tags = GherkinParser.parse(input).scenarios[0].tags
        assertTrue("@slow" in tags)
        assertTrue("@admin" in tags)
    }

    @Test
    fun `multi-tag line is parsed correctly`() {
        val input = """
            Feature: F
              @tag1 @tag2 @tag3
              Scenario: S
                Given step
        """.trimIndent()
        assertEquals(setOf("@tag1", "@tag2", "@tag3"), GherkinParser.parse(input).scenarios[0].tags)
    }

    @Test
    fun `feature without tags has empty tag set`() {
        val input = """
            Feature: F
              Scenario: S
                Given step
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertTrue(feature.tags.isEmpty())
        assertTrue(feature.scenarios[0].tags.isEmpty())
    }

    @Test
    fun `expanded outline rows carry fully resolved tags`() {
        val input = """
            @feature-tag
            Feature: F
              @outline-tag
              Scenario Outline: test <n>
                Given <n>
                @examples-tag
                Examples:
                  | n |
                  | 1 |
                  | 2 |
        """.trimIndent()
        val feature = GherkinParser.parse(input)
        assertEquals(2, feature.scenarios.size)
        for (scenario in feature.scenarios) {
            assertTrue("@feature-tag" in scenario.tags)
            assertTrue("@outline-tag" in scenario.tags)
            assertTrue("@examples-tag" in scenario.tags)
        }
    }
}
