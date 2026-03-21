package io.mcol.behave.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureFileParserTest {

    @Test
    fun `parses basic scenario steps`() {
        val feature = """
            Feature: Login
              Scenario: S
                Given I am on the login page
                When I enter "admin" as username
                Then I see the dashboard
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(3, parsed.steps.size)
        assertEquals("Given", parsed.steps[0].keyword)
        assertEquals("I am on the login page", parsed.steps[0].text)
    }

    @Test
    fun `Background steps are included`() {
        val feature = """
            Feature: F
              Background:
                Given I have a collection "words"
              Scenario: S
                When I do something
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(2, parsed.steps.size)
        assertTrue(parsed.steps.any { it.keyword == "Given" && it.text.contains("I have a collection") })
    }

    @Test
    fun `duplicate steps are deduplicated`() {
        val feature = """
            Feature: F
              Background:
                Given I am logged in
              Scenario: first
                Given I am logged in
                When I do something
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(2, parsed.steps.size)
    }

    @Test
    fun `scenario outline templates preserved with variable tokens`() {
        val feature = """
            Feature: F
              Scenario Outline: login as <role>
                Given I am logged in as <role>
                Examples:
                  | role  |
                  | admin |
                  | user  |
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(1, parsed.steps.size)
        assertTrue(parsed.steps[0].text.contains("<role>"))
    }

    @Test
    fun `detects DataTable and extracts column headers`() {
        val feature = """
            Feature: F
              Scenario: S
                Given the following cats:
                  | name   | color |
                  | Whisky | black |
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        val step = parsed.steps.first()
        assertTrue(step.hasDataTable)
        assertEquals(listOf("name", "color"), step.tableColumns)
    }

    @Test
    fun `normalises step text for deduplication`() {
        val n1 = FeatureFileParser.normalise("Given", "I have {int} items")
        val n2 = FeatureFileParser.normalise("Given", "I have {string} items")
        assertEquals(n1, n2) // both normalise to "given i have {} items"
    }

    @Test
    fun `And and But steps are included`() {
        val feature = """
            Feature: F
              Scenario: S
                Given step
                And another
                But not this
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(3, parsed.steps.size)
    }
}
