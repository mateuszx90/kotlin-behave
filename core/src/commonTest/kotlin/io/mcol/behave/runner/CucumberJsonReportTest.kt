package io.mcol.behave.runner

import io.mcol.behave.model.Background
import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CucumberJsonReportTest {

    private val feature = Feature(
        name = "Login <flow>",
        background = Background(listOf(Step(Keyword.GIVEN, "the app is open"))),
        tags = setOf("@auth"),
        scenarios = listOf(
            Scenario("passes", listOf(Step(Keyword.WHEN, "I log in"), Step(Keyword.THEN, "I see the dashboard"))),
            Scenario("fails", listOf(Step(Keyword.WHEN, "I click"), Step(Keyword.THEN, "it works")), tags = setOf("@wip")),
            Scenario("pends", listOf(Step(Keyword.THEN, "TODO step"))),
            Scenario("filtered", listOf(Step(Keyword.WHEN, "skip me"))),
        ),
    )

    private val result = RunResult(
        featureName = "Login <flow>",
        scenarios = listOf(
            ScenarioResult("passes", passed = true),
            ScenarioResult("fails", passed = false, error = RuntimeException("boom \"x\""), failedStep = "it works"),
            ScenarioResult("pends", passed = false, pending = true, failedStep = "TODO step"),
            ScenarioResult("filtered", passed = false, skipped = true),
        ),
    )

    private val json = CucumberJsonReport.render(listOf(CucumberJsonReport.FeatureRun(feature, result)))

    @Test
    fun `document is a JSON array of feature objects`() {
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertContains(json, "\"keyword\":\"Feature\"")
        assertContains(json, "\"name\":\"Login <flow>\"")
    }

    @Test
    fun `feature uri and tags are emitted`() {
        assertContains(json, "\"uri\":\"login-flow.feature\"")
        assertContains(json, "\"name\":\"@auth\"")
    }

    @Test
    fun `each scenario becomes a scenario element`() {
        assertContains(json, "\"type\":\"scenario\"")
        assertEquals(4, Regex("\"type\":\"scenario\"").findAll(json).count())
    }

    @Test
    fun `background steps are prepended to every scenario`() {
        // "the app is open" appears once per scenario element (4 times).
        assertEquals(4, Regex("the app is open").findAll(json).count())
    }

    @Test
    fun `passing scenario marks every step passed`() {
        // Background + 2 steps for "passes" — at least three passed statuses overall.
        assertContains(json, "\"status\":\"passed\"")
        assertContains(json, "\"keyword\":\"When \"")
    }

    @Test
    fun `failing step carries failed status and an escaped error message`() {
        assertContains(json, "\"status\":\"failed\"")
        assertContains(json, "\"error_message\":\"boom \\\"x\\\"\"")
    }

    @Test
    fun `step after the failing one is skipped, step before is passed`() {
        // For "fails": background(passed) -> "I click"(passed) -> "it works"(failed).
        assertContains(json, "\"name\":\"it works\",\"line\":")
        assertContains(json, "\"status\":\"pending\"") // the "pends" scenario
    }

    @Test
    fun `tag-filtered scenario has only skipped steps and no error message`() {
        assertContains(json, "\"status\":\"skipped\"")
        assertFalse(json.contains("\"error_message\":null"))
    }

    @Test
    fun `single feature render matches the array entry`() {
        val single = CucumberJsonReport.renderFeature(CucumberJsonReport.FeatureRun(feature, result))
        assertEquals("[$single]", json)
    }
}
