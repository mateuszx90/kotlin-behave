/**
 * ## Example 23: Gherkin keyword coverage
 *
 * Exercises the constructs added to kotlin-behave end-to-end (parsed AND run):
 * - `*` step bullet — inherits the previous Given/When/Then.
 * - `Example:` — synonym for `Scenario:`.
 * - `Scenario Template:` — synonym for `Scenario Outline:`.
 * - `Scenarios:` — synonym for `Examples:`.
 * - `Rule:` with its own `Background:` — the rule background runs for the rule's scenarios.
 * - table cell escapes: `\|` → literal `|`, and an empty cell → `""` (read via DataTable.asMap()).
 *
 * Manually wired (no @BehaveFeature): the feature is loaded and run by `gherkin(...)`.
 */
package io.mcol.behave.examples.ex23_gherkin_keywords

import io.mcol.behave.steps.steps
import kotlin.test.assertEquals

class KeywordCtx {
    var counter = 0
    var config: Map<String?, String?> = emptyMap()
}

val keywordSteps = steps({ KeywordCtx() }) {
    Given("a counter starting at 0") { ctx.counter = 0 }
    When("I increment it") { ctx.counter++ }
    When("I increment it {int} times") { params -> repeat(params[0] as Int) { ctx.counter++ } }
    Then("the counter is {int}") { params -> assertEquals(params[0] as Int, ctx.counter) }

    Given("the following config:") { params -> ctx.config = params.dataTable!!.asMap() }
    Then("config {string} is {string}") { params ->
        assertEquals(params[1] as String, ctx.config[params[0] as String])
    }
    Then("config {string} is empty") { params -> assertEquals("", ctx.config[params[0] as String]) }
}
