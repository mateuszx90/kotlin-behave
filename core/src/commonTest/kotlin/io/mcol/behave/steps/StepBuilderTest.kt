package io.mcol.behave.steps

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StepBuilderTest {
    class Ctx {
        var value: Int = 0
    }

    // region step registration ----------------------------------------------

    @Test
    fun `registered Given step is found by matching text`() = runTest {
        val defs =
            steps(::Ctx) {
                Given("I have {int} words") { params ->
                    val n = params[0] as Int
                    ctx.value = n
                }
            }
        assertNotNull(defs.find("I have 5 words"))
    }

    @Test
    fun `step lambda receives converted params and updates ctx`() = runTest {
        val defs =
            steps(::Ctx) {
                Given("I have {int} words") { params ->
                    val n = params[0] as Int
                    ctx.value = n
                }
            }
        defs.stepBuilder.ctx = Ctx()
        defs.find("I have 7 words")!!.invoke()
        assertEquals(7, defs.stepBuilder.ctx.value)
    }

    @Test
    fun `ctx is replaced per scenario`() = runTest {
        val defs =
            steps(::Ctx) {
                Given("set {int}") { params ->
                    val n = params[0] as Int
                    ctx.value = n
                }
            }
        defs.stepBuilder.ctx = Ctx().also { it.value = 99 }
        val fresh = defs.factory()
        defs.stepBuilder.ctx = fresh
        assertEquals(0, defs.stepBuilder.ctx.value)
    }

    @Test
    fun `unregistered step text returns null`() = runTest {
        val defs =
            steps(::Ctx) {
                Given("I have {int} words") { }
            }
        assertEquals(null, defs.find("unknown step"))
    }

    // endregion

    // region composition ----------------------------------------------------

    @Test
    fun `two StepDefinitions can be merged with + operator`() = runTest {
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
    fun `pending throws PendingException`() = runTest {
        val defs =
            steps(::Ctx) {
                Given("not done yet") { pending() }
            }
        assertFailsWith<PendingException> {
            defs.find("not done yet")!!.invoke()
        }
    }

    // endregion
}
