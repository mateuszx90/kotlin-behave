package io.mcol.behave.ksp

/**
 * Lightweight raw-text Gherkin parser for the KSP processor.
 *
 * Unlike the runtime GherkinParser, this parser:
 * - Does NOT expand Scenario Outline rows — preserves `<variable>` tokens as-is
 * - Preserves the original step text with all placeholder/variable tokens intact
 * - Is used at compile time only, not at runtime
 */
internal object FeatureFileParser {

    data class ParsedStep(
        val keyword: String,       // "Given" | "When" | "Then" | "And" | "But"
        val text: String,          // step text with {placeholders} and <variables> preserved
        val hasDataTable: Boolean, // true if a | table follows this step
        val tableColumns: List<String>, // column headers if DataTable present
    )

    data class ParsedFeature(
        val steps: List<ParsedStep>, // all unique steps (Background + Scenarios + Outlines pre-expansion)
    )

    private val keywords = listOf("Given", "When", "Then", "And", "But")
    private val sectionKeywords = listOf("Feature:", "Background:", "Scenario:", "Scenario Outline:", "Examples:")

    fun parse(featureText: String): ParsedFeature {
        val lines = featureText.lines().map { it.trim() }
        val allSteps = mutableListOf<ParsedStep>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val keyword = keywords.firstOrNull { line.startsWith("$it ") }
            if (keyword != null) {
                val text = line.removePrefix("$keyword ").trim()
                // Look ahead for DataTable
                var hasTable = false
                val tableColumns = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size) {
                    val next = lines[j].trim()
                    when {
                        next.isBlank() || next.startsWith("#") -> { j++; continue }
                        next.startsWith("|") -> {
                            if (!hasTable) {
                                // First | row = header
                                tableColumns.addAll(parseTableRow(next))
                                hasTable = true
                            }
                            j++
                        }
                        else -> break
                    }
                }
                allSteps.add(ParsedStep(keyword, text, hasTable, tableColumns))
            }
            i++
        }

        // Deduplicate by normalised text
        val seen = mutableSetOf<String>()
        val unique = allSteps.filter { step ->
            val normalised = normalise(step.keyword, step.text)
            seen.add(normalised)
        }

        return ParsedFeature(unique)
    }

    /** Normalise for deduplication: lowercase, replace {placeholder}, <variable>, and "literal" with {}, trim. */
    fun normalise(keyword: String, text: String): String {
        var s = "$keyword $text".lowercase()
        s = s.replace(Regex("\"[^\"]*\""), "{}")   // "literal" and "<variable>" treated as placeholder
        s = s.replace(Regex("\\{[^}]+}"), "{}")
        s = s.replace(Regex("<[^>]+>"), "{}")
        return s.trim()
    }

    private fun parseTableRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim() }.filter { it.isNotEmpty() }
}
