package io.mcol.behave.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureFileParserTest {
    @Test
    fun `parses basic scenario steps`() {
        val feature =
            """
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
        val feature =
            """
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
        val feature =
            """
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
        val feature =
            """
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
        val feature =
            """
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
        assertEquals(n1, n2) // both normalise to "i have {} items"
    }

    @Test
    fun `quoted literals normalise like placeholders for deduplication`() {
        val n1 = FeatureFileParser.normalise("Then", "I see the question word \"pies\"")
        val n2 = FeatureFileParser.normalise("Then", "I see the question word \"kot\"")
        assertEquals(n1, n2) // "pies" and "kot" both become {}
    }

    @Test
    fun `quoted literal steps with different literals are deduplicated`() {
        val feature =
            """
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
    fun `And and But resolve to previous keyword`() {
        val feature =
            """
            Feature: F
              Scenario: S
                Given step
                And another
                But not this
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(3, parsed.steps.size)
        assertEquals("Given", parsed.steps[0].keyword)
        assertEquals("Given", parsed.steps[1].keyword, "And should resolve to Given")
        assertEquals("Given", parsed.steps[2].keyword, "But should resolve to Given")
    }

    @Test
    fun `And after When resolves to When`() {
        val feature =
            """
            Feature: F
              Scenario: S
                Given setup
                When I do something
                And I do another thing
                Then result
                And another result
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals("Given", parsed.steps[0].keyword)
        assertEquals("When", parsed.steps[1].keyword)
        assertEquals("When", parsed.steps[2].keyword, "And after When should resolve to When")
        assertEquals("Then", parsed.steps[3].keyword)
        assertEquals("Then", parsed.steps[4].keyword, "And after Then should resolve to Then")
    }

    @Test
    fun `same step text with When and And deduplicates to one method`() {
        val feature =
            """
            Feature: F
              Scenario: first
                When I navigate to step 2
              Scenario: second
                When I start the timer
                And I navigate to step 1
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        val navigateSteps = parsed.steps.filter { it.text.contains("I navigate to step") }
        assertEquals(1, navigateSteps.size, "When and And with same text should deduplicate")
        assertEquals("When", navigateSteps[0].keyword)
    }

    @Test
    fun `allStepInstances preserves all concrete values before deduplication`() {
        val feature =
            """
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
        val feature =
            """
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
        val feature =
            """
            Feature: F
              Background:
                Given I am logged in
              Scenario: first
                When I do something
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.allStepInstances.any { it.keyword == "Given" })
    }

    @Test
    fun `extractConcreteValues extracts quoted strings and numbers from raw text`() {
        val template = """I create a recipe with 4 portions named "Pasta""""
        val raw = """I create a recipe with 2 portions named "Salad""""
        val values = TypeValidator.extractConcreteValues(raw, template)
        assertEquals(listOf("2", "Salad"), values)
    }

    @Test
    fun `extractConcreteValues handles doubles`() {
        val template = "the value is 5.5"
        val raw = "the value is 3.14"
        val values = TypeValidator.extractConcreteValues(raw, template)
        assertEquals(listOf("3.14"), values)
    }

    @Test
    fun `extractConcreteValues handles outline variables`() {
        val template = "I am logged in as <role>"
        val raw = "I am logged in as admin"
        val values = TypeValidator.extractConcreteValues(raw, template)
        assertEquals(listOf("admin"), values)
    }

    @Test
    fun `normalise replaces standalone numbers with placeholder for deduplication`() {
        val n1 = FeatureFileParser.normalise("When", """I create a recipe with 4 portions named "Pasta"""")
        val n2 = FeatureFileParser.normalise("When", """I create a recipe with 2 portions named "Salad"""")
        assertEquals(n1, n2)
    }

    @Test
    fun `steps with different number literals are deduplicated`() {
        val feature =
            """
            Feature: F
              Scenario: first
                When I create a recipe with 4 portions named "Pasta"
              Scenario: second
                When I create a recipe with 2 portions named "Salad"
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        val createSteps = parsed.steps.filter { it.text.contains("create a recipe") }
        assertEquals(1, createSteps.size, "Steps with different numbers should deduplicate")
    }

    @Test
    fun `normalise does not replace numbers embedded in words`() {
        val normalised = FeatureFileParser.normalise("When", "I visit step2go website")
        assertTrue(normalised.contains("step2go"))
    }

    @Test
    fun `Scenario Outline steps are expanded into allStepInstances`() {
        val feature =
            """
            Feature: F
              Scenario Outline: Click
                When I click <count> times
                Examples:
                  | count |
                  | 3     |
                  | 5     |
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        val clickInstances = parsed.allStepInstances.filter { it.text.contains("click") }
        assertEquals(2, clickInstances.size)
        assertTrue(clickInstances.any { it.text == "I click 3 times" })
        assertTrue(clickInstances.any { it.text == "I click 5 times" })
    }

    @Test
    fun `Outline expanded steps include scenario name with row values`() {
        val feature =
            """
            Feature: F
              Scenario Outline: Login as <role>
                Given I am logged in as <role>
                Examples:
                  | role  |
                  | admin |
                  | user  |
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        val adminStep = parsed.allStepInstances.first { it.text.contains("admin") }
        assertTrue(adminStep.scenarioName.contains("admin"))
    }

    @Test
    fun `quoted literal and outline variable in same position unify to string`() {
        val feature =
            """
            Feature: F
              Scenario: concrete
                When I search for "hello"
              Scenario Outline: outline
                When I search for <term>
                Examples:
                  | term  |
                  | world |
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        // Both should deduplicate to 1 step
        assertEquals(1, parsed.steps.size)
        // The step's text should contain the quoted form (first occurrence)
        assertTrue(parsed.steps[0].text.contains("\"hello\"") || parsed.steps[0].text.contains("<term>"))
        // allStepTemplates should contain both pre-dedup variants for unification
        assertEquals(2, parsed.allStepTemplates.size)
        assertTrue(parsed.allStepTemplates.any { it.text.contains("\"hello\"") })
        assertTrue(parsed.allStepTemplates.any { it.text.contains("<term>") })
    }

    @Test
    fun `type validation patterns match correctly`() {
        val patterns = TypeValidator.typeValidationPatterns
        assertTrue(patterns["int"]!!.matches("4"))
        assertTrue(patterns["int"]!!.matches("-3"))
        assertFalse(patterns["int"]!!.matches("5.5"))
        assertTrue(patterns["double"]!!.matches("5.5"))
        assertFalse(patterns["double"]!!.matches("4"))
        assertTrue(patterns["boolean"]!!.matches("true"))
        assertFalse(patterns["boolean"]!!.matches("True"))
    }

    // --- Malformed feature file error handling ---

    @Test
    fun `reports error when Feature keyword is missing`() {
        val feature =
            """
            Scenario: S
              Given something
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("Missing Feature:") })
    }

    @Test
    fun `reports error when file is empty`() {
        val parsed = FeatureFileParser.parse("")
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("Missing Feature:") })
    }

    @Test
    fun `reports error when file contains only whitespace`() {
        val parsed = FeatureFileParser.parse("   \n\n   ")
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("Missing Feature:") })
    }

    @Test
    fun `reports error when step appears before any Scenario or Background`() {
        val feature =
            """
            Feature: F
              Given I am orphaned
              Scenario: S
                When I do something
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("I am orphaned") })
    }

    @Test
    fun `reports single error for consecutive orphaned steps under Feature`() {
        val feature =
            """
            Feature: Basic steps

                Given I am on the login page
                When I enter valid credentials
                Then I see the dashboard
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.errors[0].message.contains("I am on the login page"))
    }

    @Test
    fun `reports error when step appears between Scenario blocks`() {
        val feature =
            """
            Feature: F
              Scenario: first
                When I do something
              Given I am orphaned between scenarios
              Scenario: second
                When I do another thing
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("I am orphaned between scenarios") })
    }

    @Test
    fun `reports one error per orphaned group not per step`() {
        val feature =
            """
            Feature: F
              Scenario: first
                When I do something
              Given orphan one
              Given orphan two
              Scenario: second
                When I do another thing
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(1, parsed.errors.size)
        assertTrue(parsed.errors[0].message.contains("orphan one"))
    }

    @Test
    fun `reports separate errors for separate orphaned groups`() {
        val feature =
            """
            Feature: F
              Scenario: first
                When I do something
              Given orphan group one
              Scenario: second
                When I do another thing
              Given orphan group two
              Scenario: third
                When I finish
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertEquals(2, parsed.errors.size)
        assertTrue(parsed.errors.any { it.message.contains("orphan group one") })
        assertTrue(parsed.errors.any { it.message.contains("orphan group two") })
    }

    @Test
    fun `error includes correct line number`() {
        val feature =
            """
            Feature: F
              Scenario: first
                When I do something
              Given I am orphaned between scenarios
              Scenario: second
                When I do another thing
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        val error = parsed.errors.first { it.message.contains("I am orphaned") }
        assertEquals(4, error.line)
    }

    @Test
    fun `feature with only Background and no Scenario is valid`() {
        val feature =
            """
            Feature: F
              Background:
                Given I am set up
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertFalse(parsed.hasErrors)
        assertEquals(1, parsed.steps.size)
        assertEquals("Given", parsed.steps[0].keyword)
    }

    @Test
    fun `feature with no scenarios produces empty steps`() {
        val feature = "Feature: Empty feature"
        val parsed = FeatureFileParser.parse(feature)
        assertFalse(parsed.hasErrors)
        assertEquals(0, parsed.steps.size)
    }

    // --- Malformed keyword syntax (inspired by common Gherkin mistakes) ---

    @Test
    fun `reports error when Feature keyword is missing colon`() {
        // "Feature  Shopping cart" — missing colon, looks like a keyword but isn't
        val feature =
            """
            Feature  Shopping cart
              Scenario: Add item
                Given the cart is empty
                When I add a product
                Then the cart has one item
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("Missing Feature:") })
    }

    @Test
    fun `reports orphaned steps when Scenario keyword is missing colon`() {
        // "Scenario  Login" — missing colon, section not recognized, steps become orphaned
        val feature =
            """
            Feature: User authentication
              Scenario  Login attempt
                Given the user is on the login page
                When they submit valid credentials
                Then they are redirected to the dashboard
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("the user is on the login page") })
    }

    @Test
    fun `reports error for file containing only plain text`() {
        val feature = "This is not a feature file at all"
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("Missing Feature:") })
    }

    @Test
    fun `silently ignores lines with unrecognised keywords`() {
        // "GivenTheUserIs..." — starts similarly but is not a Gherkin keyword
        val feature =
            """
            Feature: Product catalogue
              Scenario: Browse products
                Given the catalogue is loaded
                GivenTheUserScrollsDown to the bottom
                Then all products are visible
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertFalse(parsed.hasErrors)
        // "GivenTheUserScrollsDown" is not a keyword — ignored, only 2 real steps
        assertEquals(2, parsed.steps.size)
    }

    @Test
    fun `feature with only comments is valid with no steps`() {
        val feature =
            """
            # This file is intentionally left blank
            # Author: test suite
            Feature: Placeholder
            # TODO: add scenarios later
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertFalse(parsed.hasErrors)
        assertEquals(0, parsed.steps.size)
    }

    @Test
    fun `reports orphaned steps when Background keyword is missing colon`() {
        val feature =
            """
            Feature: Order processing
              Background  Common setup
                Given the inventory is stocked
              Scenario: Place order
                When I submit an order
                Then the order is confirmed
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertTrue(parsed.errors.any { it.message.contains("the inventory is stocked") })
    }

    @Test
    fun `missing Feature error points to first Scenario line`() {
        val feature =
            """
            Scenario: Checkout flow
              Given items are in the basket
              When payment is completed
              Then an order confirmation is sent
            """.trimIndent()
        val parsed = FeatureFileParser.parse(feature)
        assertTrue(parsed.hasErrors)
        assertEquals(1, parsed.errors[0].line) // first Scenario: is on line 1
    }
}
