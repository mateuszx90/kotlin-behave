package io.mcol.behave.steps

import io.mcol.behave.parser.GherkinParser
import io.mcol.behave.runner.GherkinRunner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HookTest {
    @Test
    fun `before and after step hooks fire around every step in order`() = runTest {
        val log = mutableListOf<String>()
        val steps = steps<Unit>({ }) {
            BeforeStep { info, _ -> log.add("before:${info.text}") }
            AfterStep { info, _ -> log.add("after:${info.text}") }
            Given("a precondition") { log.add("run:a precondition") }
            When("an action") { log.add("run:an action") }
        }
        val feature = GherkinParser.parse(
            """
            Feature: F
              Scenario: S
                Given a precondition
                When an action
            """.trimIndent(),
        )
        GherkinRunner(steps).run(feature)

        assertEquals(
            listOf(
                "before:a precondition",
                "run:a precondition",
                "after:a precondition",
                "before:an action",
                "run:an action",
                "after:an action",
            ),
            log,
        )
    }

    @Test
    fun `after step still runs when the step fails`() = runTest {
        val log = mutableListOf<String>()
        val steps = steps<Unit>({ }) {
            AfterStep { info, _ -> log.add("after:${info.text}") }
            Given("a failing step") { error("boom") }
        }
        val feature = GherkinParser.parse(
            """
            Feature: F
              Scenario: S
                Given a failing step
            """.trimIndent(),
        )
        val result = GherkinRunner(steps).run(feature)

        assertEquals(listOf("after:a failing step"), log)
        assertEquals(false, result.scenarios[0].passed)
    }

    @Test
    fun `BeforeAll and AfterAll register suite-level hooks`() {
        val steps = steps<Unit>({ }) {
            BeforeAll { }
            AfterAll { }
            AfterAll { }
        }
        assertEquals(1, steps.stepBuilder.beforeAllHooks.size)
        assertEquals(2, steps.stepBuilder.afterAllHooks.size)
    }
}
