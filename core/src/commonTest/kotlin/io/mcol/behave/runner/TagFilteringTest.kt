package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import io.mcol.behave.steps.ScenarioInfo
import io.mcol.behave.steps.steps
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TagFilteringTest {

    class Ctx

    private fun makeFeature() = Feature(
        name = "Tag filtering",
        scenarios = listOf(
            Scenario("smoke scenario", listOf(Step(Keyword.GIVEN, "step")), tags = setOf("@smoke")),
            Scenario("wip scenario", listOf(Step(Keyword.GIVEN, "step")), tags = setOf("@wip")),
        ),
    )

    // region run() — suspend variant

    @Test
    fun `no filter runs all scenarios`() = runTest {
        val defs = steps(::Ctx) { Given("step") { } }
        val result = GherkinRunner(defs, tags = null).run(makeFeature())
        assertTrue(result.scenarios.all { it.passed })
    }

    @Test
    fun `tag filter skips non-matching scenarios`() = runTest {
        val defs = steps(::Ctx) { Given("step") { } }
        val result = GherkinRunner(defs, tags = "@smoke").run(makeFeature())
        assertTrue(result.scenarios[0].passed)
        assertTrue(result.scenarios[1].skipped)
        assertFalse(result.hasFailures)
    }

    @Test
    fun `after hooks receive fresh ctx for skipped scenarios in run`() = runTest {
        val ctxPerScenario = mutableListOf<Ctx>()
        val defs = steps(::Ctx) {
            After { info: ScenarioInfo, ctx: Ctx -> ctxPerScenario.add(ctx) }
            Given("step") { }
        }
        GherkinRunner(defs, tags = "@smoke").run(makeFeature())
        assertEquals(2, ctxPerScenario.size, "After hook should run for both scenarios")
        assertNotSame(
            ctxPerScenario[0],
            ctxPerScenario[1],
            "Skipped scenario must receive its own fresh ctx, not the previous scenario's ctx",
        )
    }

    // endregion

    // region runWithPerScenarioRunner()

    @Test
    fun `per-scenario runner skips non-matching scenarios`() {
        val ran = mutableListOf<String>()
        val defs = steps(::Ctx) { Given("step") { ran.add("ran") } }
        val result = GherkinRunner(defs, tags = "@smoke").runWithPerScenarioRunner(makeFeature()) { _, run -> run() }
        assertEquals(1, ran.size)
        assertTrue(result.scenarios[0].passed)
        assertTrue(result.scenarios[1].skipped)
    }

    @Test
    fun `per-scenario runner does not call after hooks for skipped scenarios`() {
        var afterHookCount = 0
        val defs = steps(::Ctx) {
            After { _: Ctx -> afterHookCount++ }
            Given("step") { }
        }
        GherkinRunner(defs, tags = "@smoke").runWithPerScenarioRunner(makeFeature()) { _, run -> run() }
        assertEquals(1, afterHookCount, "After hook should only run for the scenario that actually ran")
    }

    // endregion
}
