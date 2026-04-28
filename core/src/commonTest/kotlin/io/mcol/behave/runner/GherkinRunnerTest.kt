package io.mcol.behave.runner

import io.mcol.behave.model.*
import io.mcol.behave.steps.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class GherkinRunnerTest {

    class Ctx { var value: Int = 0 }

    private fun simpleFeature(vararg scenarioSteps: Step) = Feature(
        name = "Test",
        scenarios = listOf(Scenario("scenario", scenarioSteps.toList())),
    )

    private fun makeSteps() = steps(::Ctx) {
        Given("I have {int} words") { (n: Int) -> ctx.value = n }
        Then("value is {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        Then("this is pending") { pending() }
    }

    // region passing ---------------------------------------------------------

    @Test
    fun `passing scenario produces passed result`() = runTest {
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
    fun `failing step marks scenario as failed`() = runTest {
        val feature = simpleFeature(
            Step(Keyword.GIVEN, "I have 5 words"),
            Step(Keyword.THEN, "value is 99"),
        )
        val result = GherkinRunner(makeSteps()).run(feature)
        assertFalse(result.scenarios[0].passed)
        assertTrue(result.hasFailures)
    }

    @Test
    fun `remaining steps are skipped after first failure`() = runTest {
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
    fun `all scenarios run even when one fails`() = runTest {
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
    fun `missing step marks scenario as failed with MissingStepException`() = runTest {
        val feature = simpleFeature(Step(Keyword.GIVEN, "no matching step"))
        val result = GherkinRunner(makeSteps()).run(feature)
        assertFalse(result.scenarios[0].passed)
        assertTrue(result.scenarios[0].error?.message?.contains("no matching step") == true)
    }

    // endregion

    // region pending --------------------------------------------------------

    @Test
    fun `pending step marks scenario as pending not failed`() = runTest {
        val feature = simpleFeature(Step(Keyword.THEN, "this is pending"))
        val result = GherkinRunner(makeSteps()).run(feature)
        assertTrue(result.scenarios[0].pending)
        assertFalse(result.scenarios[0].passed)
        assertFalse(result.hasFailures)
    }

    // endregion

    // region background -----------------------------------------------------

    @Test
    fun `background steps run before each scenario`() = runTest {
        val defs = steps(::Ctx) {
            Given("setup") { ctx.value = 10 }
            Then("value is {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        }
        val feature = Feature(
            name = "F",
            background = Background(listOf(Step(Keyword.GIVEN, "setup"))),
            scenarios = listOf(
                Scenario("first",  listOf(Step(Keyword.THEN, "value is 10"))),
                Scenario("second", listOf(Step(Keyword.THEN, "value is 10"))),
            ),
        )
        assertFalse(GherkinRunner(defs).run(feature).hasFailures)
    }

    @Test
    fun `ctx is fresh for each scenario`() = runTest {
        val defs = steps(::Ctx) {
            Given("set {int}") { (n: Int) -> ctx.value = n }
            Then("value is {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("first",  listOf(Step(Keyword.GIVEN, "set 1"), Step(Keyword.THEN, "value is 1"))),
            Scenario("second", listOf(Step(Keyword.GIVEN, "set 2"), Step(Keyword.THEN, "value is 2"))),
        ))
        assertFalse(GherkinRunner(defs).run(feature).hasFailures)
    }

    @Test
    fun `background runs before each expanded outline scenario`() = runTest {
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
        assertFalse(GherkinRunner(defs).run(feature).hasFailures)
    }

    // endregion

    // region hooks -----------------------------------------------------------

    @Test
    fun `Before hook runs once per scenario`() = runTest {
        var beforeCount = 0
        val defs = steps(::Ctx) {
            Before { beforeCount++ }
            Given("step") { }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("first",  listOf(Step(Keyword.GIVEN, "step"))),
            Scenario("second", listOf(Step(Keyword.GIVEN, "step"))),
        ))
        GherkinRunner(defs).run(feature)
        assertEquals(2, beforeCount)
    }

    @Test
    fun `After hook always runs even when step fails`() = runTest {
        var afterRan = false
        val defs = steps(::Ctx) {
            After { afterRan = true }
            Given("fail") { throw AssertionError("boom") }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("s", listOf(Step(Keyword.GIVEN, "fail")))
        ))
        GherkinRunner(defs).run(feature)
        assertTrue(afterRan)
    }

    @Test
    fun `Before hook failure skips steps but After still runs`() = runTest {
        var stepRan = false
        var afterRan = false
        val defs = steps(::Ctx) {
            Before { throw AssertionError("before failed") }
            After  { afterRan = true }
            Given("step") { stepRan = true }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("s", listOf(Step(Keyword.GIVEN, "step")))
        ))
        val result = GherkinRunner(defs).run(feature)
        assertTrue(result.hasFailures)
        assertFalse(stepRan)
        assertTrue(afterRan)
    }

    @Test
    fun `After hooks run in reverse registration order`() = runTest {
        val order = mutableListOf<Int>()
        val defs = steps(::Ctx) {
            After { order.add(1) }
            After { order.add(2) }
            Given("step") { }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("s", listOf(Step(Keyword.GIVEN, "step")))
        ))
        GherkinRunner(defs).run(feature)
        assertEquals(listOf(2, 1), order)
    }

    @Test
    fun `hooks are preserved when step definitions are combined with plus`() = runTest {
        var beforeCount = 0
        val defs1 = steps(::Ctx) {
            Before { beforeCount++ }
            Given("step1") { }
        }
        val defs2 = steps(::Ctx) {
            Before { beforeCount++ }
            Given("step2") { }
        }
        val combined = defs1 + defs2
        val feature = Feature("F", scenarios = listOf(
            Scenario("s", listOf(Step(Keyword.GIVEN, "step1"), Step(Keyword.GIVEN, "step2")))
        ))
        GherkinRunner(combined).run(feature)
        assertEquals(2, beforeCount)
    }

    // endregion

    // region per-scenario runner (non-suspend) --------------------------------
    // These tests do NOT use runTest — runWithPerScenarioRunner is non-suspend.

    @Test
    fun `per-scenario runner is called once per scenario`() {
        var callCount = 0
        val defs = steps(::Ctx) { Given("step") { } }
        val feature = Feature("F", scenarios = listOf(
            Scenario("first",  listOf(Step(Keyword.GIVEN, "step"))),
            Scenario("second", listOf(Step(Keyword.GIVEN, "step"))),
        ))
        GherkinRunner(defs).runWithPerScenarioRunner(feature) { _, run ->
            callCount++
            run()
        }
        assertEquals(2, callCount)
    }

    @Test
    fun `per-scenario runner receives fresh ctx for each scenario`() {
        data class TrackCtx(var id: Int = 0)
        var idCounter = 0
        val defs = steps({ TrackCtx(++idCounter) }) { Given("step") { } }
        val seenIds = mutableListOf<Int>()
        val feature = Feature("F", scenarios = listOf(
            Scenario("first",  listOf(Step(Keyword.GIVEN, "step"))),
            Scenario("second", listOf(Step(Keyword.GIVEN, "step"))),
        ))
        GherkinRunner(defs).runWithPerScenarioRunner(feature) { ctx, run ->
            seenIds.add(ctx.id)
            run()
        }
        assertEquals(2, seenIds.size)
        assertFalse(seenIds[0] == seenIds[1])
    }

    @Test
    fun `per-scenario runner executes Before hooks inside run lambda`() {
        var beforeCount = 0
        val defs = steps(::Ctx) {
            Before { beforeCount++ }
            Given("step") { }
        }
        val feature = Feature("F", scenarios = listOf(
            Scenario("s", listOf(Step(Keyword.GIVEN, "step")))
        ))
        GherkinRunner(defs).runWithPerScenarioRunner(feature) { _, run ->
            assertEquals(0, beforeCount)
            run()
            assertEquals(1, beforeCount)
        }
    }

    // endregion

    @Test
    fun `reads feature from resources and runs it`() = runTest {
        val defs = steps(::Ctx) {
            Given("the value is {int}") { (n: Int) -> ctx.value = n }
            Then("value equals {int}") { (n: Int) -> kotlin.test.assertEquals(n, ctx.value) }
        }
        val content = readResource("features/hello.feature")
        val feature = io.mcol.behave.parser.GherkinParser.parse(content)
        assertFalse(GherkinRunner(defs).run(feature).hasFailures)
    }

    // region data table -------------------------------------------------------

    @Test
    fun `data table rows are passed to step definition via params`() = runTest {
        val capturedRows = mutableListOf<Map<String, String?>>()
        val defs = steps(::Ctx) {
            Given("the following words") { params ->
                capturedRows.addAll(params.dataTable?.rows ?: emptyList())
            }
        }
        val table = DataTable(listOf(
            mapOf("word" to "Hund",  "translation" to "dog"),
            mapOf("word" to "Katze", "translation" to "cat"),
            mapOf("word" to "Haus",  "translation" to "house"),
        ))
        val feature = simpleFeature(Step(Keyword.GIVEN, "the following words", table))
        assertFalse(GherkinRunner(defs).run(feature).hasFailures)
        assertEquals(3, capturedRows.size)
        assertEquals(mapOf<String, String?>("word" to "Hund",  "translation" to "dog"),   capturedRows[0])
        assertEquals(mapOf<String, String?>("word" to "Katze", "translation" to "cat"),   capturedRows[1])
        assertEquals(mapOf<String, String?>("word" to "Haus",  "translation" to "house"), capturedRows[2])
    }

    @Test
    fun `data table rows parsed from feature file and passed to step definition`() = runTest {
        val capturedRows = mutableListOf<Map<String, String?>>()
        val defs = steps(::Ctx) {
            Given("the following words") { params ->
                capturedRows.addAll(params.dataTable?.rows ?: emptyList())
            }
            Then("word count is {int}") { (n: Int) -> assertEquals(n, capturedRows.size) }
        }
        gherkin("features/data_table.feature", defs)
        assertEquals(3, capturedRows.size)
        assertEquals(mapOf<String, String?>("word" to "Hund",  "translation" to "dog"),   capturedRows[0])
        assertEquals(mapOf<String, String?>("word" to "Katze", "translation" to "cat"),   capturedRows[1])
        assertEquals(mapOf<String, String?>("word" to "Haus",  "translation" to "house"), capturedRows[2])
    }

    @Test
    fun `cell with text null is parsed as null value`() = runTest {
        val capturedRows = mutableListOf<Map<String, String?>>()
        val defs = steps(::Ctx) {
            Given("the following words") { params ->
                capturedRows.addAll(params.dataTable?.rows ?: emptyList())
            }
            Then("word count is {int}") { (n: Int) -> assertEquals(n, capturedRows.size) }
        }
        gherkin("features/data_table_null.feature", defs)
        assertEquals(null,  capturedRows[0]["translation"]) // "null" text → null
        assertEquals("cat", capturedRows[1]["translation"]) // normal value
    }

    @Test
    fun `step without table has null dataTable in params`() = runTest {
        var captured: io.mcol.behave.model.DataTable? = DataTable(emptyList())
        val defs = steps(::Ctx) {
            Given("plain step") { params -> captured = params.dataTable }
        }
        val feature = simpleFeature(Step(Keyword.GIVEN, "plain step"))
        GherkinRunner(defs).run(feature)
        assertEquals(null, captured)
    }

    // endregion

    @Test
    fun `end-to-end gherkin runs feature file and passes`() = runTest {
        class WordCtx { var count: Int = 0 }
        val wordSteps = steps(::WordCtx) {
            Given("the adapter count is {int}") { (n: Int) -> ctx.count = n }
            Given("adapter count is {int}") { (n: Int) -> ctx.count = n }
            Then("currently learning count is {int}") { (n: Int) ->
                kotlin.test.assertEquals(n, minOf(ctx.count, 10))
            }
        }
        gherkin("features/word_list.feature", wordSteps)
    }
}
