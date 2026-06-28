package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import io.mcol.behave.steps.steps
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryTest {

    class Ctx

    /**
     * A deterministically-flaky step factory: the returned step throws on every attempt before
     * [failUntil], then passes. The attempt counter is external to the ctx so it survives the
     * fresh-ctx-per-attempt reset.
     */
    private fun flakySteps(attempts: IntArray, failUntil: Int) = steps(::Ctx) {
        When("the flaky service is called") {
            attempts[0]++
            if (attempts[0] < failUntil) throw AssertionError("transient failure #${attempts[0]}")
        }
        Then("it eventually succeeds") { }
    }

    private fun flakyFeature(tags: Set<String> = emptySet()) = Feature(
        name = "Flaky",
        scenarios = listOf(
            Scenario(
                name = "eventually passes",
                steps = listOf(
                    Step(Keyword.WHEN, "the flaky service is called"),
                    Step(Keyword.THEN, "it eventually succeeds"),
                ),
                tags = tags,
            ),
        ),
    )

    @Test
    fun `without retries a flaky scenario fails`() = runTest {
        val attempts = intArrayOf(0)
        val result = GherkinRunner(flakySteps(attempts, failUntil = 2)).run(flakyFeature())
        assertTrue(result.hasFailures)
        assertEquals(1, attempts[0], "Expected exactly one attempt when retries are disabled")
    }

    @Test
    fun `one retry lets a fail-once-then-pass scenario succeed`() = runTest {
        val attempts = intArrayOf(0)
        val result = GherkinRunner(flakySteps(attempts, failUntil = 2), retries = 1).run(flakyFeature())
        assertFalse(result.hasFailures)
        assertEquals(2, attempts[0], "Expected a second attempt that passes")
    }

    @Test
    fun `retries are exhausted when the scenario never recovers`() = runTest {
        val attempts = intArrayOf(0)
        val result = GherkinRunner(flakySteps(attempts, failUntil = 99), retries = 2).run(flakyFeature())
        assertTrue(result.hasFailures)
        assertEquals(3, attempts[0], "Expected the initial attempt plus two retries")
    }

    @Test
    fun `flaky tag grants a default retry budget without an explicit count`() = runTest {
        val attempts = intArrayOf(0)
        val result = GherkinRunner(flakySteps(attempts, failUntil = 2)).run(flakyFeature(tags = setOf("@flaky")))
        assertFalse(result.hasFailures)
        assertEquals(2, attempts[0])
    }

    @Test
    fun `per-scenario runner also retries failing scenarios`() = runTest {
        val attempts = intArrayOf(0)
        var runnerCalls = 0
        val result = GherkinRunner(flakySteps(attempts, failUntil = 2), retries = 1)
            .runWithPerScenarioRunner(flakyFeature()) { _, run ->
                runnerCalls++
                run()
            }
        assertFalse(result.hasFailures)
        assertEquals(2, attempts[0])
        assertEquals(2, runnerCalls, "Expected the per-scenario runner to be invoked once per attempt")
    }
}
