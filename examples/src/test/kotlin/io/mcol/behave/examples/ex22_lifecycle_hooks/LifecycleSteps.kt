/**
 * ## Example 22: Lifecycle hooks — BeforeAll / AfterAll / BeforeStep / AfterStep
 *
 * - `BeforeAll { ... }` / `AfterAll { ... }` run ONCE per feature file (wired via the Kotest
 *   beforeSpec/afterSpec). They take no scenario ctx — use them for suite-wide setup/teardown.
 * - `BeforeStep { info, ctx -> ... }` / `AfterStep { info, ctx -> ... }` run around EVERY step;
 *   AfterStep runs even when the step fails (handy for screenshots / diagnostics).
 *
 * Both scenarios below observe `Suite.beforeAll == 1`, proving BeforeAll ran once, not per scenario.
 * Hooks are added on the manually-wired `steps()` builder (the KSP companion has only step routing).
 */
package io.mcol.behave.examples.ex22_lifecycle_hooks

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.steps.steps
import kotlin.test.assertEquals

object Suite {
    var beforeAll = 0
    var afterAll = 0
    val stepLog = mutableListOf<String>()
}

@BehaveFeature("features/22_lifecycle_hooks.feature", generateTest = false)
class LifecycleSteps : LifecycleStepsSpec {
    override suspend fun theSuiteIsInitialised() {
        assertEquals(1, Suite.beforeAll, "BeforeAll must run exactly once before the feature")
    }

    override suspend fun beforeAllRanExactlyOnce() {
        assertEquals(1, Suite.beforeAll)
        // Step hooks wrapped this step's predecessor (the Given) — proves BeforeStep/AfterStep fired.
        assertEquals(true, Suite.stepLog.contains("before:the suite is initialised"))
        assertEquals(true, Suite.stepLog.contains("after:the suite is initialised"))
    }
}

val lifecycleSteps = steps({ LifecycleSteps() }) {
    BeforeAll { Suite.beforeAll++ }
    AfterAll { Suite.afterAll++ }
    BeforeStep { info, _ -> Suite.stepLog.add("before:${info.text}") }
    AfterStep { info, _ -> Suite.stepLog.add("after:${info.text}") }

    Given("the suite is initialised") { ctx.theSuiteIsInitialised() }
    Then("before-all ran exactly once") { ctx.beforeAllRanExactlyOnce() }
}
