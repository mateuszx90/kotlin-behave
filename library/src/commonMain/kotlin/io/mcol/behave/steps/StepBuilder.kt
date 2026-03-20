package io.mcol.behave.steps

import io.mcol.behave.types.Params

// 'ctx' is a var on StepBuilder. Step lambdas are defined inside steps {} where
// 'this' is StepBuilder<C>, so they capture the StepBuilder instance and read
// ctx via the outer receiver. The runner swaps stepBuilder.ctx before each scenario.
class StepBuilder<C>(val factory: () -> C) {
    var ctx: C = factory()
    private val entries = mutableListOf<StepEntry<C>>()
    internal val beforeHooks = mutableListOf<() -> Unit>()
    internal val afterHooks  = mutableListOf<() -> Unit>()

    fun Before(fn: () -> Unit) { beforeHooks.add(fn) }
    fun After(fn: () -> Unit)  { afterHooks.add(fn) }

    fun Given(expr: String, fn: (Params) -> Unit) = register(expr, fn)
    fun When(expr: String, fn: (Params) -> Unit)  = register(expr, fn)
    fun Then(expr: String, fn: (Params) -> Unit)  = register(expr, fn)
    fun And(expr: String, fn: (Params) -> Unit)   = register(expr, fn)
    fun But(expr: String, fn: (Params) -> Unit)   = register(expr, fn)

    fun pending(message: String = "Step is pending"): Nothing = throw PendingException(message)

    private fun register(expr: String, fn: (Params) -> Unit) {
        entries.add(StepEntry(expr, fn))
    }

    internal fun build(): StepDefinitions<C> = StepDefinitions(factory, this, entries.toList())
}

fun <C> steps(factory: () -> C, block: StepBuilder<C>.() -> Unit): StepDefinitions<C> {
    val builder = StepBuilder(factory)
    builder.block()
    return builder.build()
}
