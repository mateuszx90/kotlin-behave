package io.mcol.behave.steps

import io.mcol.behave.model.DataTable
import io.mcol.behave.model.Step
import io.mcol.behave.types.Params
import io.mcol.behave.types.TypeRegistry

class DuplicateStepException(
    expr: String,
) : Exception("Duplicate step expression: \"$expr\"")

class PendingException(
    msg: String = "Step is pending",
) : Exception(msg)

class MissingStepException(
    text: String,
) : Exception("No step definition found for: \"$text\"")

data class StepEntry<C>(
    val expression: String,
    val fn: suspend (Params) -> Unit,
)

class StepDefinitions<C>(
    val factory: () -> C,
    val stepBuilder: StepBuilder<C>,
    internal val entries: List<StepEntry<C>>,
    internal val typeRegistry: TypeRegistry = TypeRegistry(),
) {
    fun find(
        stepText: String,
        dataTable: DataTable? = null,
        docString: String? = null,
    ): (suspend () -> Unit)? {
        for (entry in entries) {
            val compiled = typeRegistry.compile(entry.expression)
            val match = compiled.regex.matchEntire(stepText) ?: continue
            val rawParams = compiled.convert(match)

            // If a DataTable is attached and a table type converter is registered,
            // map rows automatically and inject the resulting List<T> into params.
            val tableConverter = typeRegistry.findTableConverter()
            val (resolvedParams, resolvedTable) =
                if (dataTable != null && tableConverter != null) {
                    val mapped = dataTable.rows.map { tableConverter(it) }
                    (rawParams + listOf(mapped)) to null
                } else {
                    rawParams to dataTable
                }

            val params = Params(resolvedParams, resolvedTable, docString)
            return { entry.fn(params) }
        }
        return null
    }

    fun find(step: Step): (suspend () -> Unit)? = find(step.text, step.dataTable, step.docString)

    operator fun plus(other: StepDefinitions<C>): StepDefinitions<C> {
        val allExpressions = entries.map { it.expression } + other.entries.map { it.expression }
        val duplicates = allExpressions.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) throw DuplicateStepException(duplicates.first())
        val mergedRegistry = typeRegistry.merge(other.typeRegistry)
        val merged =
            StepBuilder(factory).also { mb ->
                mb.beforeHooks.addAll(stepBuilder.beforeHooks)
                mb.beforeHooks.addAll(other.stepBuilder.beforeHooks)
                mb.afterHooks.addAll(stepBuilder.afterHooks)
                mb.afterHooks.addAll(other.stepBuilder.afterHooks)
                mb.beforeAllHooks.addAll(stepBuilder.beforeAllHooks)
                mb.beforeAllHooks.addAll(other.stepBuilder.beforeAllHooks)
                mb.afterAllHooks.addAll(stepBuilder.afterAllHooks)
                mb.afterAllHooks.addAll(other.stepBuilder.afterAllHooks)
                mb.beforeStepHooks.addAll(stepBuilder.beforeStepHooks)
                mb.beforeStepHooks.addAll(other.stepBuilder.beforeStepHooks)
                mb.afterStepHooks.addAll(stepBuilder.afterStepHooks)
                mb.afterStepHooks.addAll(other.stepBuilder.afterStepHooks)
            }
        return StepDefinitions(factory, merged, entries + other.entries, mergedRegistry)
    }
}
