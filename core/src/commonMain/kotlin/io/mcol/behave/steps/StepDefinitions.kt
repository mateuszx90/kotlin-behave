package io.mcol.behave.steps

import io.mcol.behave.model.DataTable
import io.mcol.behave.model.Step
import io.mcol.behave.types.Params
import io.mcol.behave.types.TypeRegistry

class DuplicateStepException(expr: String) : Exception("Duplicate step expression: \"$expr\"")
class PendingException(msg: String = "Step is pending") : Exception(msg)
class MissingStepException(text: String) : Exception("No step definition found for: \"$text\"")

data class StepEntry<C>(
    val expression: String,
    val fn: suspend (Params) -> Unit,
)

class StepDefinitions<C>(
    val factory: () -> C,
    val stepBuilder: StepBuilder<C>,
    internal val entries: List<StepEntry<C>>,
) {
    fun find(stepText: String, dataTable: DataTable? = null): (suspend () -> Unit)? {
        for (entry in entries) {
            val compiled = TypeRegistry.compile(entry.expression)
            val match = compiled.regex.matchEntire(stepText) ?: continue
            val params = Params(compiled.convert(match), dataTable)
            return { entry.fn(params) }
        }
        return null
    }

    fun find(step: Step): (suspend () -> Unit)? = find(step.text, step.dataTable)

    operator fun plus(other: StepDefinitions<C>): StepDefinitions<C> {
        val allExpressions = entries.map { it.expression } + other.entries.map { it.expression }
        val duplicates = allExpressions.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) throw DuplicateStepException(duplicates.first())
        val merged = StepBuilder(factory).also { mb ->
            mb.beforeHooks.addAll(stepBuilder.beforeHooks)
            mb.beforeHooks.addAll(other.stepBuilder.beforeHooks)
            mb.afterHooks.addAll(stepBuilder.afterHooks)
            mb.afterHooks.addAll(other.stepBuilder.afterHooks)
        }
        return StepDefinitions(factory, merged, entries + other.entries)
    }
}
