package io.mcol.behave.steps

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import io.mcol.behave.runner.GherkinRunner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HookOverloadsTest {
    class Ctx

    private fun feature(vararg tags: String) = Feature(
        "F",
        scenarios = listOf(Scenario("s", listOf(Step(Keyword.GIVEN, "step")), tags = tags.toSet())),
    )

    @Test
    fun `Before ctx hook runs`() = runTest {
        var ran = false
        val defs =
            steps(::Ctx) {
                Before { _: Ctx -> ran = true }
                Given("step") { }
            }
        GherkinRunner(defs).run(feature())
        assertTrue(ran)
    }

    @Test
    fun `Before with-ctx hook receives context`() = runTest {
        var received: Ctx? = null
        val defs =
            steps(::Ctx) {
                Before { c: Ctx -> received = c }
                Given("step") { }
            }
        GherkinRunner(defs).run(feature())
        assertTrue(received != null)
    }

    @Test
    fun `Before with-scenario-and-ctx hook receives scenario name`() = runTest {
        var receivedName = ""
        val defs =
            steps(::Ctx) {
                Before { info: ScenarioInfo, _: Ctx -> receivedName = info.name }
                Given("step") { }
            }
        GherkinRunner(defs).run(feature())
        assertEquals("s", receivedName)
    }

    @Test
    fun `After hook sees Passed status on success`() = runTest {
        var status: ScenarioStatus? = null
        val defs =
            steps(::Ctx) {
                After { info: ScenarioInfo, _: Ctx -> status = info.status }
                Given("step") { }
            }
        GherkinRunner(defs).run(feature())
        assertEquals(ScenarioStatus.Passed, status)
    }

    @Test
    fun `After hook sees Failed status on step failure`() = runTest {
        var status: ScenarioStatus? = null
        val defs =
            steps(::Ctx) {
                After { info: ScenarioInfo, _: Ctx -> status = info.status }
                Given("fail") { throw AssertionError("boom") }
            }
        val f = Feature("F", scenarios = listOf(Scenario("s", listOf(Step(Keyword.GIVEN, "fail")))))
        GherkinRunner(defs).run(f)
        assertEquals(ScenarioStatus.Failed, status)
    }

    @Test
    fun `After hook sees Pending status on pending step`() = runTest {
        var status: ScenarioStatus? = null
        val defs =
            steps(::Ctx) {
                After { info: ScenarioInfo, _: Ctx -> status = info.status }
                Given("pending step") { pending() }
            }
        val f = Feature("F", scenarios = listOf(Scenario("s", listOf(Step(Keyword.GIVEN, "pending step")))))
        GherkinRunner(defs).run(f)
        assertEquals(ScenarioStatus.Pending, status)
    }

    @Test
    fun `After hook receives scenario tags`() = runTest {
        var receivedTags = emptySet<String>()
        val defs =
            steps(::Ctx) {
                After { info: ScenarioInfo, _: Ctx -> receivedTags = info.tags }
                Given("step") { }
            }
        GherkinRunner(defs).run(feature("@smoke"))
        assertEquals(setOf("@smoke"), receivedTags)
    }

    @Test
    fun `both Before overloads run in order`() = runTest {
        val log = mutableListOf<String>()
        val defs =
            steps(::Ctx) {
                Before { _: Ctx -> log.add("with-ctx") }
                Before { _: ScenarioInfo, _: Ctx -> log.add("with-scenario-and-ctx") }
                Given("step") { }
            }
        GherkinRunner(defs).run(feature())
        assertEquals(listOf("with-ctx", "with-scenario-and-ctx"), log)
    }
}
