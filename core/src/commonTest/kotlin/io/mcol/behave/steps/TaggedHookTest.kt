package io.mcol.behave.steps

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import io.mcol.behave.runner.GherkinRunner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TaggedHookTest {

    class Ctx

    private fun feature() = Feature(
        name = "Tagged hooks",
        scenarios = listOf(
            Scenario("uses db", listOf(Step(Keyword.GIVEN, "step")), tags = setOf("@db")),
            Scenario("uses web", listOf(Step(Keyword.GIVEN, "step")), tags = setOf("@web")),
            Scenario("plain", listOf(Step(Keyword.GIVEN, "step"))),
        ),
    )

    @Test
    fun `tagged Before runs only for matching scenarios`() = runTest {
        val ran = mutableListOf<String>()
        val defs = steps(::Ctx) {
            Before("@db") { ran.add("db") }
            Given("step") { }
        }
        GherkinRunner(defs).run(feature())
        assertEquals(listOf("db"), ran, "Before(\"@db\") should fire once, for the @db scenario only")
    }

    @Test
    fun `tagged After runs only for matching scenarios`() = runTest {
        val ran = mutableListOf<String>()
        val defs = steps(::Ctx) {
            After("@web") { ran.add("web") }
            Given("step") { }
        }
        GherkinRunner(defs).run(feature())
        assertEquals(listOf("web"), ran)
    }

    @Test
    fun `tag expressions support and or not`() = runTest {
        val ran = mutableListOf<String>()
        val defs = steps(::Ctx) {
            Before("@db or @web") { ran.add("matched") }
            Given("step") { }
        }
        GherkinRunner(defs).run(feature())
        assertEquals(listOf("matched", "matched"), ran, "Should fire for both @db and @web, not the plain scenario")
    }

    @Test
    fun `untagged hooks still run for every scenario`() = runTest {
        var count = 0
        val defs = steps(::Ctx) {
            Before { count++ }
            Given("step") { }
        }
        GherkinRunner(defs).run(feature())
        assertEquals(3, count)
    }

    @Test
    fun `tagged Before with ScenarioInfo only fires for matching scenarios`() = runTest {
        val seen = mutableListOf<String>()
        val defs = steps(::Ctx) {
            Before("@db") { info: ScenarioInfo, _ -> seen.add(info.name) }
            Given("step") { }
        }
        GherkinRunner(defs).run(feature())
        assertEquals(listOf("uses db"), seen)
    }
}
