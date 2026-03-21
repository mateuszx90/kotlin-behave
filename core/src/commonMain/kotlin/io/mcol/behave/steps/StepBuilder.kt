package io.mcol.behave.steps

import io.mcol.behave.types.Params

class StepBuilder<C>(val factory: () -> C) {
    var ctx: C = factory()
    private val entries = mutableListOf<StepEntry<C>>()
    val beforeHooks = mutableListOf<suspend () -> Unit>()
    val afterHooks  = mutableListOf<suspend () -> Unit>()

    fun Before(fn: suspend () -> Unit) { beforeHooks.add(fn) }
    fun After(fn: suspend () -> Unit)  { afterHooks.add(fn) }

    fun Given(expr: String, fn: suspend (Params) -> Unit) = register(expr, fn)
    fun When(expr: String, fn: suspend (Params) -> Unit)  = register(expr, fn)
    fun Then(expr: String, fn: suspend (Params) -> Unit)  = register(expr, fn)
    fun And(expr: String, fn: suspend (Params) -> Unit)   = register(expr, fn)
    fun But(expr: String, fn: suspend (Params) -> Unit)   = register(expr, fn)

    fun pending(message: String = "Step is pending"): Nothing = throw PendingException(message)

    private fun register(expr: String, fn: suspend (Params) -> Unit) {
        entries.add(StepEntry(expr, fn))
    }

    internal fun build(): StepDefinitions<C> = StepDefinitions(factory, this, entries.toList())
}

fun <C> steps(factory: () -> C, block: StepBuilder<C>.() -> Unit): StepDefinitions<C> {
    val builder = StepBuilder(factory)
    builder.block()
    return builder.build()
}
