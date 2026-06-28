/**
 * ## Example 30: Per-step timeout
 *
 * Demonstrates:
 * - `gherkin(..., stepTimeoutMillis = N)` bounds every individual step; a step that runs
 *   longer fails the scenario with a TimeoutCancellationException.
 *
 * The steps here are instant, so the scenario passes comfortably inside the 2s budget. See
 * docs/kotest.md for parallel scenario execution via Kotest's concurrency config.
 */
package io.mcol.behave.examples.ex30_step_timeout

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin
import io.mcol.behave.steps.steps

class TimeoutCtx {
    var done = false
}

val stepTimeoutSteps = steps(::TimeoutCtx) {
    Given("a quick operation") { ctx.done = true }
    Then("it finished in time") { check(ctx.done) }
}

/** Runs the feature with a 2-second per-step budget. */
class StepTimeoutGherkinTest :
    FreeSpec({
        gherkin("features/30_step_timeout.feature", stepTimeoutSteps, stepTimeoutMillis = 2_000)
    })
