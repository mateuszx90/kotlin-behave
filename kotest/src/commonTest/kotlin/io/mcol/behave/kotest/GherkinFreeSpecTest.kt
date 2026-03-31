package io.mcol.behave.kotest

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.steps.steps

// ── Happy path + hook verification ──────────────────────────────────────────

class CounterCtx {
    var count: Int = 0
    var setupRan: Boolean = false
}

private val hookLog = mutableListOf<String>()

private val counterSteps =
    steps(::CounterCtx) {
        Before {
            hookLog.add("before")
            ctx.setupRan = true
        }
        After { hookLog.add("after") }

        Given("the counter is {int}") { (n: Int) -> ctx.count = n }
        When("I increment it") { ctx.count++ }
        Then("the counter is {int}") { (n: Int) ->
            kotlin.test.assertEquals(n, ctx.count)
        }
        Then("the setup hook ran") {
            kotlin.test.assertTrue(ctx.setupRan, "Expected Before hook to have set setupRan = true")
        }
    }

class GherkinFreeSpecTest :
    FreeSpec({
        beforeSpec { hookLog.clear() }

        gherkin("features/counter.feature", counterSteps)

        // After all counter scenarios ran, verify hook log has one before+after per scenario
        afterSpec {
            val scenarioCount = 3
            val beforeCount = hookLog.count { it == "before" }
            val afterCount = hookLog.count { it == "after" }
            kotlin.test.assertEquals(scenarioCount, beforeCount, "Expected Before hook once per scenario")
            kotlin.test.assertEquals(scenarioCount, afterCount, "Expected After hook once per scenario")
        }
    })
