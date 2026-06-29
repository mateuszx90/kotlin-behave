package io.mcol.behave.ksp

/**
 * Compile-time type validation for concrete step values.
 *
 * Extracts concrete values from raw step text by comparing against the template step,
 * then validates those values match the expected type patterns.
 */
internal object TypeValidator {
    // Scalar patterns come from the shared source of truth so the validator can never drift from the
    // runtime step matcher (TypeRegistry). `string` is validator-specific: the value reaching here has
    // already had its surrounding quotes stripped by the capture group, so it matches anything.
    val typeValidationPatterns: Map<String, Regex> =
        io.mcol.behave.gherkin.GherkinTypes.builtinValuePatterns.mapValues { Regex(it.value) } +
            ("string" to Regex(".*"))

    /**
     * Extract concrete values from [rawText] by building a regex from [templateText].
     *
     * The template contains dynamic tokens (quoted strings, numbers, outline variables)
     * which are replaced with capture groups. The resulting regex is matched against the
     * raw text to extract the concrete values.
     */
    fun extractConcreteValues(
        rawText: String,
        templateText: String,
    ): List<String> {
        data class Token(
            val range: IntRange,
            val captureGroup: String,
        )

        val tokens = mutableListOf<Token>()

        // Quoted strings: "..." -> capture unquoted content
        Regex("\"[^\"]*\"").findAll(templateText).forEach {
            tokens.add(Token(it.range, "\"([^\"]*)\""))
        }

        // Outline variables: <...> (not inside quotes)
        val quotedRanges = tokens.map { it.range }
        Regex("<[^>]+>")
            .findAll(templateText)
            .filter { m -> quotedRanges.none { m.range.first in it } }
            .forEach { tokens.add(Token(it.range, "(\\S+)")) }

        // Standalone doubles (before integers to avoid partial matches)
        Regex("""(?<!\S)-?\d+\.\d+(?!\S)""")
            .findAll(templateText)
            .filter { m -> tokens.none { m.range.first in it.range } }
            .forEach { tokens.add(Token(it.range, "(-?\\d+\\.\\d+)")) }

        // Standalone integers
        Regex("""(?<!\S)-?\d+(?!\S)""")
            .findAll(templateText)
            .filter { m -> tokens.none { m.range.first in it.range } }
            .forEach { tokens.add(Token(it.range, "(-?\\d+)")) }

        if (tokens.isEmpty()) return emptyList()

        val sorted = tokens.sortedBy { it.range.first }
        val sb = StringBuilder()
        var pos = 0
        for (tok in sorted) {
            if (tok.range.first > pos) {
                sb.append(Regex.escape(templateText.substring(pos, tok.range.first)))
            }
            sb.append(tok.captureGroup)
            pos = tok.range.last + 1
        }
        if (pos < templateText.length) {
            sb.append(Regex.escape(templateText.substring(pos)))
        }

        val match = Regex(sb.toString()).find(rawText) ?: return emptyList()
        return match.groupValues.drop(1)
    }

    /**
     * Extract placeholder type names from a step expression like "I have {int} items named {string}".
     */
    fun extractPlaceholderTypes(stepExpression: String): List<String> = Regex("\\{([^}]+)}").findAll(stepExpression).map { it.groupValues[1] }.toList()
}
