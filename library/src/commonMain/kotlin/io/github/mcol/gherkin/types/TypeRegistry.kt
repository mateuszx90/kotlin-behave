package io.github.mcol.gherkin.types

import kotlin.text.MatchResult
import kotlin.text.Regex

data class CompiledExpression(
    val regex: Regex,
    val convert: (MatchResult) -> List<Any>,
)

object TypeRegistry {

    private data class PlaceholderDef(
        val pattern: String,
        val regexGroup: String,
        val convert: (String) -> Any,
    )

    private val placeholders = listOf(
        PlaceholderDef("{int}",    """-?\d+""",    { it.toInt() }),
        PlaceholderDef("{long}",   """-?\d+""",    { it.toLong() }),
        PlaceholderDef("{float}",  """-?\d+\.?\d*""", { it.toFloat() }),
        PlaceholderDef("{double}", """-?\d+\.?\d*""", { it.toDouble() }),
        PlaceholderDef("{string}", """"[^"]*"""",  { it.removeSurrounding("\"") }),
        PlaceholderDef("{word}",   """\S+""",      { it }),
    )

    fun compile(expression: String): CompiledExpression {
        var pattern = expression
        val converters = mutableListOf<(String) -> Any>()
        val isCucumber = placeholders.any { expression.contains(it.pattern) }

        if (isCucumber) {
            // Replace each placeholder with a capture group
            for (ph in placeholders) {
                while (pattern.contains(ph.pattern)) {
                    pattern = pattern.replaceFirst(ph.pattern, "(${ph.regexGroup})")
                    converters.add(ph.convert)
                }
            }
            val regex = Regex("^$pattern$")
            return CompiledExpression(regex) { match ->
                match.groupValues.drop(1).mapIndexed { i, v -> converters[i](v) }
            }
        } else {
            // Raw regex — all capture groups return String
            val regex = Regex("^$expression$")
            return CompiledExpression(regex) { match ->
                match.groupValues.drop(1).map { it }
            }
        }
    }
}
