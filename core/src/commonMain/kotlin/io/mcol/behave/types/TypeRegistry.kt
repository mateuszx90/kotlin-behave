package io.mcol.behave.types

import kotlin.text.MatchResult
import kotlin.text.Regex

data class CompiledExpression(
    val regex: Regex,
    val convert: (MatchResult) -> List<Any>,
)

class TypeRegistry {
    private data class PlaceholderDef(
        val name: String,
        val pattern: String,
        val regexGroup: String,
        val convert: (String) -> Any,
    )

    private data class TableTypeDef(
        val name: String,
        val convert: (Map<String, String?>) -> Any,
    )

    private val builtins =
        listOf(
            PlaceholderDef("{int}", "{int}", """-?\d+""", { it.toInt() }),
            PlaceholderDef("{long}", "{long}", """-?\d+""", { it.toLong() }),
            PlaceholderDef("{float}", "{float}", """-?\d+\.?\d*""", { it.toFloat() }),
            PlaceholderDef("{double}", "{double}", """-?\d+\.?\d*""", { it.toDouble() }),
            PlaceholderDef("{string}", "{string}", """"[^"]*"""", { it.removeSurrounding("\"") }),
            PlaceholderDef("{word}", "{word}", """\S+""", { it }),
            PlaceholderDef("{boolean}", "{boolean}", """true|false""", { it.toBooleanStrict() }),
        )

    private val customScalar = mutableListOf<PlaceholderDef>()
    private val tableTypes = mutableListOf<TableTypeDef>()

    fun register(
        name: String,
        pattern: String,
        convert: (String) -> Any,
    ) {
        val placeholder = "{$name}"
        customScalar.removeAll { it.name == placeholder }
        customScalar.add(PlaceholderDef(placeholder, placeholder, pattern, convert))
    }

    fun registerTableType(
        name: String,
        convert: (Map<String, String?>) -> Any,
    ) {
        tableTypes.removeAll { it.name == name }
        tableTypes.add(TableTypeDef(name, convert))
    }

    fun findTableConverter(): ((Map<String, String?>) -> Any)? = tableTypes.firstOrNull()?.convert

    fun merge(other: TypeRegistry): TypeRegistry {
        val thisNames = (customScalar.map { it.name } + tableTypes.map { it.name }).toSet()
        val otherNames = (other.customScalar.map { it.name } + other.tableTypes.map { it.name }).toSet()
        val conflicts = thisNames intersect otherNames
        if (conflicts.isNotEmpty()) {
            error("TypeRegistry merge conflict: duplicate type name(s): ${conflicts.joinToString()}")
        }
        val merged = TypeRegistry()
        merged.customScalar.addAll(customScalar)
        merged.customScalar.addAll(other.customScalar)
        merged.tableTypes.addAll(tableTypes)
        merged.tableTypes.addAll(other.tableTypes)
        return merged
    }

    /** Active placeholders: custom scalars first (override builtins with same name), then builtins. */
    private fun activePlaceholders(): List<PlaceholderDef> {
        val customNames = customScalar.map { it.name }.toSet()
        return customScalar + builtins.filter { it.name !in customNames }
    }

    fun compile(expression: String): CompiledExpression {
        val active = activePlaceholders()
        var pattern = expression
        val isCucumber = active.any { expression.contains(it.pattern) }

        if (isCucumber) {
            // Collect (position, converter) pairs so converters are ordered by
            // their left-to-right position in the expression, not by placeholder
            // definition order.
            val positioned = mutableListOf<Pair<Int, (String) -> Any>>()
            for (ph in active) {
                while (pattern.contains(ph.pattern)) {
                    val pos = pattern.indexOf(ph.pattern)
                    pattern = pattern.replaceFirst(ph.pattern, "(${ph.regexGroup})")
                    positioned.add(pos to ph.convert)
                }
            }
            val converters = positioned.sortedBy { it.first }.map { it.second }
            val regex = Regex("^$pattern$")
            return CompiledExpression(regex) { match ->
                match.groupValues.drop(1).mapIndexed { i, v -> converters[i](v) }
            }
        } else {
            val regex = Regex("^$expression$")
            return CompiledExpression(regex) { match ->
                match.groupValues.drop(1).map { it }
            }
        }
    }
}
