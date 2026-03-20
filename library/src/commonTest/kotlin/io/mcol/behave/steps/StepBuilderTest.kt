package io.mcol.behave.steps

import io.mcol.behave.types.Params
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StepBuilderTest {

    class Ctx { var value: Int = 0 }

    // region step registration ----------------------------------------------

    @Test
    fun `registered Given step is found by matching text`() {
        val defs = steps(::Ctx) {
            Given("I have {int} words") { (n: Int) -> ctx.value = n }
        }
        val entry = defs.find("I have 5 words")
        assertNotNull(entry)
    }

    @Test
    fun `step lambda receives converted params and updates ctx`() {
        val defs = steps(::Ctx) {
            Given("I have {int} words") { (n: Int) -> ctx.value = n }
        }
        // Swap ctx to a fresh instance (simulates runner behaviour)
        defs.stepBuilder.ctx = Ctx()
        // find() returns a no-arg lambda that closes over the step's captured StepBuilder
        defs.find("I have 7 words")!!.invoke()
        assertEquals(7, defs.stepBuilder.ctx.value)
    }

    @Test
    fun `ctx is replaced per scenario`() {
        val defs = steps(::Ctx) {
            Given("set {int}") { (n: Int) -> ctx.value = n }
        }
        defs.stepBuilder.ctx = Ctx().also { it.value = 99 }
        val fresh = defs.factory()
        defs.stepBuilder.ctx = fresh
        assertEquals(0, defs.stepBuilder.ctx.value)
    }

    @Test
    fun `unregistered step text returns null`() {
        val defs = steps(::Ctx) {
            Given("I have {int} words") { }
        }
        assertEquals(null, defs.find("unknown step"))
    }

    // endregion

    // region composition ----------------------------------------------------

    @Test
    fun `two StepDefinitions can be merged with + operator`() {
        val a = steps(::Ctx) { Given("step A") { ctx.value = 1 } }
        val b = steps(::Ctx) { Given("step B") { ctx.value = 2 } }
        val merged = a + b
        assertNotNull(merged.find("step A"))
        assertNotNull(merged.find("step B"))
    }

    @Test
    fun `duplicate expression throws DuplicateStepException at merge time`() {
        val a = steps(::Ctx) { Given("same step") { } }
        val b = steps(::Ctx) { Given("same step") { } }
        assertFailsWith<DuplicateStepException> { a + b }
    }

    // endregion

    // region pending --------------------------------------------------------

    @Test
    fun `pending() throws PendingException`() {
        val defs = steps(::Ctx) {
            Given("not done yet") { pending() }
        }
        assertFailsWith<PendingException> {
            defs.find("not done yet")!!.invoke()
        }
    }

    // endregion
}
