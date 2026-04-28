package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import io.mcol.behave.steps.ScenarioInfo
import io.mcol.behave.steps.ScenarioStatus
import io.mcol.behave.steps.steps
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagsRunnerTest {
    class Ctx

    private fun makeFeature() = Feature(
        name = "F",
        scenarios =
        listOf(
            Scenario("smoke scenario", listOf(Step(Keyword.GIVEN, "step")), tags = setOf("@smoke")),
            Scenario("wip scenario", listOf(Step(Keyword.GIVEN, "step")), tags = setOf("@wip")),
        ),
    )

    @Test
    fun `tag filter skips non-matching scenarios`() = runTest {
        val defs = steps(::Ctx) { Given("step") { } }
        val result = GherkinRunner(defs, tags = "@smoke").run(makeFeature())
        assertTrue(result.scenarios[0].passed)
        assertTrue(result.scenarios[1].skipped)
        assertFalse(result.hasFailures)
    }

    @Test
    fun `skipped scenarios are not counted as failures`() = runTest {
        val defs = steps(::Ctx) { Given("step") { } }
        val result = GherkinRunner(defs, tags = "@smoke").run(makeFeature())
        assertFalse(result.hasFailures)
    }

    @Test
    fun `null tag filter runs all scenarios`() = runTest {
        val defs = steps(::Ctx) { Given("step") { } }
        val result = GherkinRunner(defs, tags = null).run(makeFeature())
        assertTrue(result.scenarios.all { it.passed })
    }

    @Test
    fun `after hooks see Skipped status for filtered scenarios`() = runTest {
        val statuses = mutableListOf<ScenarioStatus>()
        val defs =
            steps(::Ctx) {
                After { info: ScenarioInfo, _: Ctx -> statuses.add(info.status) }
                Given("step") { }
            }
        GherkinRunner(defs, tags = "@smoke").run(makeFeature())
        assertTrue(ScenarioStatus.Passed in statuses)
        assertTrue(ScenarioStatus.Skipped in statuses)
    }

    @Test
    fun `per-scenario runner skips filtered scenarios`() = runTest {
        val ran = mutableListOf<String>()
        val defs = steps(::Ctx) { Given("step") { ran.add("ran") } }
        val result = GherkinRunner(defs, tags = "@smoke").runWithPerScenarioRunner(makeFeature()) { _, run -> run() }
        assertEquals(1, ran.size)
        assertTrue(result.scenarios[1].skipped)
    }
}
