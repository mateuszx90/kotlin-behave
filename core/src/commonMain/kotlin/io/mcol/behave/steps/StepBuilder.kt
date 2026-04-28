package io.mcol.behave.steps

import io.mcol.behave.types.Params
import io.mcol.behave.types.TypeRegistry

sealed interface Hook<C> {
    class WithCtx<C>(val block: suspend (C) -> Unit) : Hook<C>
    class WithScenarioAndCtx<C>(val block: suspend (ScenarioInfo, C) -> Unit) : Hook<C>
}

class StepBuilder<C>(val factory: () -> C) {
    var ctx: C = factory()
    private val entries = mutableListOf<StepEntry<C>>()
    val beforeHooks = mutableListOf<Hook<C>>()
    val afterHooks  = mutableListOf<Hook<C>>()
    val typeRegistry = TypeRegistry()

    // Before overloads — `Before { }` resolves to the ctx overload with ignored parameter
    fun Before(fn: suspend (C) -> Unit) { beforeHooks.add(Hook.WithCtx(fn)) }
    fun Before(fn: suspend (ScenarioInfo, C) -> Unit) { beforeHooks.add(Hook.WithScenarioAndCtx(fn)) }

    // After overloads
    fun After(fn: suspend (C) -> Unit) { afterHooks.add(Hook.WithCtx(fn)) }
    fun After(fn: suspend (ScenarioInfo, C) -> Unit) { afterHooks.add(Hook.WithScenarioAndCtx(fn)) }

    fun Given(expr: String, fn: suspend (Params) -> Unit) = register(expr, fn)
    fun When(expr: String, fn: suspend (Params) -> Unit)  = register(expr, fn)
    fun Then(expr: String, fn: suspend (Params) -> Unit)  = register(expr, fn)
    fun And(expr: String, fn: suspend (Params) -> Unit)   = register(expr, fn)
    fun But(expr: String, fn: suspend (Params) -> Unit)   = register(expr, fn)

    fun pending(message: String = "Step is pending"): Nothing = throw PendingException(message)

    inline fun <reified T> parameterType(name: String, pattern: String, noinline convert: (String) -> T) {
        typeRegistry.register(name, pattern, convert as (String) -> Any)
    }

    inline fun <reified T> parameterType(name: String, noinline convert: (Map<String, String?>) -> T) {
        typeRegistry.registerTableType(name, convert as (Map<String, String?>) -> Any)
    }

    private fun register(expr: String, fn: suspend (Params) -> Unit) {
        entries.add(StepEntry(expr, fn))
    }

    internal fun build(): StepDefinitions<C> = StepDefinitions(factory, this, entries.toList(), typeRegistry)
}

fun <C> steps(factory: () -> C, block: StepBuilder<C>.() -> Unit): StepDefinitions<C> {
    val builder = StepBuilder(factory)
    builder.block()
    return builder.build()
}
