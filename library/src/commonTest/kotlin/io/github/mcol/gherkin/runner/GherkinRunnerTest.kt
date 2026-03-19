package io.github.mcol.gherkin.runner

import io.github.mcol.gherkin.model.*
import io.github.mcol.gherkin.steps.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GherkinRunnerTest {

    class Ctx { var value: Int = 0 }

    private fun simpleFeature(vararg scenarioSteps: Step) = Feature(
        name = "Test",
        scenarios = listOf(Scenario("scenario", scenarioSteps.toList())),
    )

    private fun makeSteps() = steps(::Ctx) {
        Given("I have {int} words") { (n: Int) -> ctx.value = n }
        Then("value is {int}") { (n: Int) ->
            kotlin.test.assertEquals(n, ctx.value)
        }
        Then("this is pending") { pending() }
    }

    // region passing ---------------------------------------------------------

    @Test
    fun `passing scenario produces passed result`() {
        val feature = simpleFeature(
            Step(Keyword.GIVEN, "I have 5 words"),
            Step(Keyword.THEN, "value is 5"),
        )
        val result = GherkinRunner(makeSteps()).run(feature)
        assertTrue(result.scenarios[0].passed)
        assertFalse(result.hasFailures)
    }

    // endregion

    // region failing ---------------------------------------------------------

    @Test
    fun `failing step marks scenario as failed`() {
        val feature = simpleFeature(
            Step(Keyword.GIVEN, "I have 5 words"),
            Step(Keyword.THEN, "value is 99"),  // will fail
        )
        val result = GherkinRunner(makeSteps()).run(feature)
        assertFalse(result.scenarios[0].passed)
        assertTrue(result.hasFailures)
    }

    @Test
    fun `remaining steps are skipped after first failure`() {
        var secondStepRan = false
        val defs = steps(::Ctx) {
            Given("fail") { throw AssertionError("boom") }
            Then("should not run") { secondStepRan = true }
        }
        val feature = simpleFeature(
            Step(Keyword.GIVEN, "fail"),
            Step(Keyword.THEN, "should not run"),
        )
        GherkinRunner(defs).run(feature)
        assertFalse(secondStepRan)
    }

    @Test
    fun `all scenarios run even when one fails`() {
        var secondScenarioRan = false
        val defs = steps(::Ctx) {
            Given("fail") { throw AssertionError("boom") }
            Given("succeed") { secondScenarioRan = true }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("first", listOf(Step(Keyword.GIVEN, "fail"))),
            Scenario("second", listOf(Step(Keyword.GIVEN, "succeed"))),
        ))
        GherkinRunner(defs).run(feature)
        assertTrue(secondScenarioRan)
    }

    // endregion

    // region missing step ---------------------------------------------------

    @Test
    fun `missing step marks scenario as failed with MissingStepException`() {
        val feature = simpleFeature(Step(Keyword.GIVEN, "no matching step"))
        val result = GherkinRunner(makeSteps()).run(feature)
        assertFalse(result.scenarios[0].passed)
        assertTrue(result.scenarios[0].error?.message?.contains("no matching step") == true)
    }

    // endregion

    // region pending --------------------------------------------------------

    @Test
    fun `pending step marks scenario as pending not failed`() {
        val feature = simpleFeature(Step(Keyword.THEN, "this is pending"))
        val result = GherkinRunner(makeSteps()).run(feature)
        assertTrue(result.scenarios[0].pending)
        assertFalse(result.scenarios[0].passed)
        assertFalse(result.hasFailures)
    }

    // endregion

    // region background -----------------------------------------------------

    @Test
    fun `background steps run before each scenario`() {
        val defs = steps(::Ctx) {
            Given("setup") { ctx.value = 10 }
            Then("value is {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        }
        val feature = Feature(
            name = "F",
            background = Background(listOf(Step(Keyword.GIVEN, "setup"))),
            scenarios = listOf(
                Scenario("first", listOf(Step(Keyword.THEN, "value is 10"))),
                Scenario("second", listOf(Step(Keyword.THEN, "value is 10"))),
            ),
        )
        val result = GherkinRunner(defs).run(feature)
        assertFalse(result.hasFailures)
    }

    @Test
    fun `ctx is fresh for each scenario`() {
        val defs = steps(::Ctx) {
            Given("set {int}") { (n: Int) -> ctx.value = n }
            Then("value is {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("first",  listOf(Step(Keyword.GIVEN, "set 1"), Step(Keyword.THEN, "value is 1"))),
            Scenario("second", listOf(Step(Keyword.GIVEN, "set 2"), Step(Keyword.THEN, "value is 2"))),
        ))
        val result = GherkinRunner(defs).run(feature)
        assertFalse(result.hasFailures)
    }

    @Test
    fun `background runs before each expanded outline scenario`() {
        val defs = steps(::Ctx) {
            Given("setup") { ctx.value = 10 }
            Given("add {int}") { (n: Int) -> ctx.value += n }
            Then("value is {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        }
        val feature = Feature(
            name = "F",
            background = Background(listOf(Step(Keyword.GIVEN, "setup"))),
            scenarios = listOf(
                Scenario("add [n=1]", listOf(Step(Keyword.GIVEN, "add 1"), Step(Keyword.THEN, "value is 11"))),
                Scenario("add [n=5]", listOf(Step(Keyword.GIVEN, "add 5"), Step(Keyword.THEN, "value is 15"))),
            ),
        )
        val result = GherkinRunner(defs).run(feature)
        assertFalse(result.hasFailures)
    }

    // endregion

    @Test
    fun `reads feature from resources and runs it`() {
        val defs = steps(::Ctx) {
            Given("the value is {int}") { (n: Int) -> ctx.value = n }
            Then("value equals {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        }
        val content = readResource("features/hello.feature")
        val feature = io.github.mcol.gherkin.parser.GherkinParser.parse(content)
        val result = GherkinRunner(defs).run(feature)
        assertFalse(result.hasFailures)
    }
}
