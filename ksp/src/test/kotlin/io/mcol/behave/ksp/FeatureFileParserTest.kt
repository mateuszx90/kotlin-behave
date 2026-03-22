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
    fun `quoted literals normalise like placeholders for deduplication`() {
        val n1 = FeatureFileParser.normalise("Then", "I see the question word \"pies\"")
        val n2 = FeatureFileParser.normalise("Then", "I see the question word \"kot\"")
        assertEquals(n1, n2) // "pies" and "kot" both become {}
    }

    @Test
    fun `quoted literal steps with different literals are deduplicated`() {
        val feature = """
            Feature: F
              Scenario: first
                When I tap "Cancel"
              Scenario: second
                When I tap "Discard"
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(1, parsed.steps.size) // deduplicated: same step pattern
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

    @Test
    fun `allStepInstances preserves all concrete values before deduplication`() {
        val feature = """
            Feature: F
              Scenario: first
                When I tap "Cancel"
              Scenario: second
                When I tap "Discard"
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(1, parsed.steps.size) // deduplicated
        assertEquals(2, parsed.allStepInstances.size) // both raw instances
        assertTrue(parsed.allStepInstances.any { it.text.contains("Cancel") })
        assertTrue(parsed.allStepInstances.any { it.text.contains("Discard") })
    }

    @Test
    fun `allStepInstances includes scenario name for error reporting`() {
        val feature = """
            Feature: F
              Scenario: Create pasta
                When I create something named "Pasta"
              Scenario: Create salad
                When I create something named "Salad"
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        val pastaInstance = parsed.allStepInstances.first { it.text.contains("Pasta") }
        assertEquals("Create pasta", pastaInstance.scenarioName)
    }

    @Test
    fun `allStepInstances includes background steps`() {
        val feature = """
            Feature: F
              Background:
                Given I am logged in
              Scenario: first
                When I do something
        """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.allStepInstances.any { it.keyword == "Given" })
    }
}
