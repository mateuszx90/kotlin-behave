/**
 * ## Example 25: i18n / localized keywords
 *
 * The feature file opens with `# language: de`, so its keywords are German
 * (Funktionalität / Grundlage / Szenario / Szenariogrundriss / Beispiele / Angenommen / Wenn /
 * Dann / Und). Only keywords are localized — the step *text* stays German, so the step definitions
 * below match the German text. The parser translates keywords to canonical English before matching.
 *
 * Manually wired (no @BehaveFeature): the feature is loaded and run by `gherkin(...)`.
 */
package io.mcol.behave.examples.ex25_localized_keywords

import io.mcol.behave.steps.steps
import kotlin.test.assertEquals

class LocalizedKeywordsCtx {
    var counter = 0
}

val localizedKeywordsSteps = steps({ LocalizedKeywordsCtx() }) {
    Given("ein Zähler bei 0") { ctx.counter = 0 }
    When("ich den Zähler erhöhe") { ctx.counter++ }
    When("ich den Zähler {int} mal erhöhe") { params -> repeat(params[0] as Int) { ctx.counter++ } }
    Then("ist der Zähler {int}") { params -> assertEquals(params[0] as Int, ctx.counter) }
}
