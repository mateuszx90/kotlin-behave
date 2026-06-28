package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import io.mcol.behave.steps.steps
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepTimeoutTest {

    class Ctx

    private fun feature(stepText: String) = Feature(
        name = "Timeouts",
        scenarios = listOf(Scenario("s", listOf(Step(Keyword.GIVEN, stepText)))),
    )

    @Test
    fun `a step exceeding the per-step timeout fails the scenario`() = runTest {
        val defs = steps(::Ctx) { Given("slow") { delay(10_000) } }
        val result = GherkinRunner(defs, stepTimeoutMillis = 100).run(feature("slow"))
        assertTrue(result.hasFailures)
        assertTrue(
            result.scenarios[0].error?.let { it::class.simpleName?.contains("Timeout") == true } == true,
            "expected a timeout error, got ${result.scenarios[0].error}",
        )
    }

    @Test
    fun `a step within the per-step timeout passes`() = runTest {
        val defs = steps(::Ctx) { Given("quick") { delay(10) } }
        val result = GherkinRunner(defs, stepTimeoutMillis = 5_000).run(feature("quick"))
        assertFalse(result.hasFailures)
    }

    @Test
    fun `no per-step timeout is enforced when zero`() = runTest {
        val defs = steps(::Ctx) { Given("slow") { delay(10_000) } }
        val result = GherkinRunner(defs, stepTimeoutMillis = 0).run(feature("slow"))
        assertFalse(result.hasFailures)
    }
}
