package io.mcol.behave.gherkin

/**
 * Single source of truth for Gherkin parameter typing, shared by the KSP processor and the
 * IntelliJ plugin so both agree on what Kotlin type a step parameter has.
 *
 * Two concerns:
 *  - the built-in placeholder type system (`{int}` ↔ `Int`, …), and
 *  - inferring an outline `<variable>`'s type from the concrete values it takes.
 */
public object GherkinTypes {
    /** Built-in placeholder name → Kotlin type (e.g. "int" -> "Int"). */
    public val placeholderToKotlin: Map<String, String> =
        mapOf(
            "string" to "String",
            "int" to "Int",
            "long" to "Long",
            "float" to "Float",
            "double" to "Double",
            "word" to "String",
            "boolean" to "Boolean",
        )

    /** Kotlin type name → canonical builtin placeholder (e.g. "Int" -> "int"). Inverse of [placeholderToKotlin]. */
    public val kotlinToPlaceholder: Map<String, String> =
        mapOf(
            "Int" to "int",
            "Long" to "long",
            "Float" to "float",
            "Double" to "double",
            "Boolean" to "boolean",
        )

    /**
     * Infer a unified Kotlin type for each outline `<variable>` in [templateText], scanning EVERY
     * concrete instance in [instanceTexts] (Examples-table rows AND standalone literal steps that
     * share this step's shape). A variable is typed (`Int`/`Long`/`Double`/`Boolean`) only when ALL
     * of its concrete values agree; any disagreement — or no instances — leaves it `String` (absent
     * from the returned map). Returns variable name → Kotlin type for the typed variables only.
     *
     * Quoted `"<var>"` is treated as a string token and never typed. Keyword is irrelevant: matching
     * is done purely on the rendered step text.
     */
    public fun inferVariableTypes(
        templateText: String,
        instanceTexts: List<String>,
    ): Map<String, String> {
        // Position-ordered tokens of the template. Only unquoted <variable> tokens carry a name;
        // every dynamic token becomes a capture group so we can read back the value each variable took.
        data class Tok(val range: IntRange, val group: String, val varName: String?)
        val toks = mutableListOf<Tok>()
        Regex("\"[^\"]*\"").findAll(templateText).forEach { toks.add(Tok(it.range, "\"([^\"]*)\"", null)) }
        val quotedRanges = toks.map { it.range }
        Regex("<([^>]+)>").findAll(templateText)
            .filter { m -> quotedRanges.none { m.range.first in it } }
            .forEach { toks.add(Tok(it.range, "(\\S+)", it.groupValues[1])) }
        Regex("""(?<!\S)-?\d+\.\d+(?!\S)""").findAll(templateText)
            .filter { m -> toks.none { m.range.first in it.range } }
            .forEach { toks.add(Tok(it.range, "(-?\\d+\\.\\d+)", null)) }
        Regex("""(?<!\S)-?\d+(?!\S)""").findAll(templateText)
            .filter { m -> toks.none { m.range.first in it.range } }
            .forEach { toks.add(Tok(it.range, "(-?\\d+)", null)) }

        val sorted = toks.sortedBy { it.range.first }
        val varGroupIndices = sorted.withIndex().filter { it.value.varName != null }
        if (varGroupIndices.isEmpty()) return emptyMap()

        // Anchored regex from the template so only true instances of THIS step match.
        val pattern = buildString {
            append("^")
            var pos = 0
            for (tok in sorted) {
                if (tok.range.first > pos) append(Regex.escape(templateText.substring(pos, tok.range.first)))
                append(tok.group)
                pos = tok.range.last + 1
            }
            if (pos < templateText.length) append(Regex.escape(templateText.substring(pos)))
            append("$")
        }
        val regex = Regex(pattern)

        val valuesByVar = mutableMapOf<String, MutableList<String>>()
        for (instance in instanceTexts) {
            val match = regex.find(instance) ?: continue
            val groups = match.groupValues.drop(1) // group 0 is the whole match
            for ((idx, tok) in varGroupIndices) {
                val v = groups.getOrNull(idx) ?: continue
                valuesByVar.getOrPut(tok.varName!!) { mutableListOf() }.add(v)
            }
        }

        // A variable is typed only when ALL its concrete values agree on one non-String type.
        return valuesByVar.mapNotNull { (name, values) ->
            if (values.isEmpty()) return@mapNotNull null
            val kotlinTypes = values.map { v ->
                when {
                    v == "true" || v == "false" -> "Boolean"
                    v.toIntOrNull() != null -> "Int"
                    v.toLongOrNull() != null -> "Long"
                    v.toDoubleOrNull() != null -> "Double"
                    else -> "String"
                }
            }.toSet()
            kotlinTypes.singleOrNull()?.takeIf { it != "String" }?.let { name to it }
        }.toMap()
    }
}
