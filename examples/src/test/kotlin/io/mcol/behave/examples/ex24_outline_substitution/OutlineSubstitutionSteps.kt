/**
 * ## Example 24: outline substitution, multiple Examples blocks and typed doc strings
 *
 * Exercises the newest runtime-parser additions end-to-end (parsed AND run):
 * - A Scenario Outline with **two** `Examples:` blocks — every row in every block becomes a scenario.
 * - `<placeholder>` substitution inside a **Doc String** and inside a **Data Table**, not only step text.
 * - The Doc String **content type** (```json) read back via `params.docStringContentType`.
 * - A `@tag` on a `Rule:` — its scenario runs normally (tag inheritance is asserted in GherkinParserTest).
 *
 * Manually wired (no @BehaveFeature): the feature is loaded and run by `gherkin(...)`.
 */
package io.mcol.behave.examples.ex24_outline_substitution

import io.mcol.behave.steps.steps
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlineSubstitutionCtx {
    var counter = 0
    var payload: String = ""
    var payloadType: String? = null
    var config: Map<String?, String?> = emptyMap()
}

val outlineSubstitutionSteps = steps({ OutlineSubstitutionCtx() }) {
    Given("a counter starting at 0") { ctx.counter = 0 }
    When("I increment it") { ctx.counter++ }
    When("I increment it {int} times") { params -> repeat(params[0] as Int) { ctx.counter++ } }
    Then("the counter is {int}") { params -> assertEquals(params[0] as Int, ctx.counter) }

    // The doc string and its content type both arrive on `params`; <name> was already substituted.
    When("I record the payload:") { params ->
        ctx.payload = params.docString ?: ""
        ctx.payloadType = params.docStringContentType
    }
    // The data table's <name> cell was substituted before the step ran.
    When("I record the config:") { params -> ctx.config = params.dataTable!!.asMap() }

    Then("the recorded payload contains {string}") { params ->
        assertTrue(ctx.payload.contains(params[0] as String), "payload was: ${ctx.payload}")
    }
    Then("the recorded payload type is {string}") { params -> assertEquals(params[0] as String, ctx.payloadType) }
    Then("the recorded config {string} is {string}") { params ->
        assertEquals(params[1] as String, ctx.config[params[0] as String])
    }
}
