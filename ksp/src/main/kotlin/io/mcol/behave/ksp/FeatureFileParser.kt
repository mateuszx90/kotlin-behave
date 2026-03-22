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

    data class RawStep(
        val keyword: String,      // "Given" | "When" | "Then" | "And" | "But"
        val text: String,         // original text before normalization (concrete values intact)
        val scenarioName: String, // for error reporting: which scenario this step appears in
    )

    data class ParsedFeature(
        val steps: List<ParsedStep>,             // all unique steps (deduplicated)
        val allStepInstances: List<RawStep>,      // all concrete values preserved (for type validation)
        val allStepTemplates: List<ParsedStep> = emptyList(), // all steps before deduplication (for type unification)
    )

    private val keywords = listOf("Given", "When", "Then", "And", "But")
    private val sectionKeywords = listOf("Feature:", "Background:", "Scenario:", "Scenario Outline:", "Examples:")

    fun parse(featureText: String): ParsedFeature {
        val lines = featureText.lines().map { it.trim() }
        val allSteps = mutableListOf<ParsedStep>()
        val allRawSteps = mutableListOf<RawStep>()
        var currentScenarioName = ""
        var inOutline = false
        var outlineName = ""
        var outlineSteps = mutableListOf<Pair<String, String>>() // keyword, text

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Track current scenario/background name and outline context
            when {
                line.startsWith("Scenario Outline:") -> {
                    inOutline = true
                    outlineName = line.removePrefix("Scenario Outline:").trim()
                    outlineSteps = mutableListOf()
                    currentScenarioName = outlineName
                }
                line.startsWith("Scenario:") -> {
                    inOutline = false
                    currentScenarioName = line.removePrefix("Scenario:").trim()
                }
                line.startsWith("Background:") -> {
                    inOutline = false
                    currentScenarioName = "Background"
                }
            }

            // Expand Scenario Outline Examples into allRawSteps
            if (line.startsWith("Examples:")) {
                var j = i + 1
                var header = emptyList<String>()
                val dataRows = mutableListOf<List<String>>()
                while (j < lines.size) {
                    val next = lines[j].trim()
                    when {
                        next.isBlank() || next.startsWith("#") -> { j++; continue }
                        next.startsWith("|") -> {
                            val cells = parseTableRow(next)
                            if (header.isEmpty()) header = cells else dataRows.add(cells)
                            j++
                        }
                        else -> break
                    }
                }
                for (row in dataRows) {
                    val substitutions = header.zip(row).toMap()
                    val expandedOutlineName = substitutions.entries.fold(outlineName) { name, (variable, value) ->
                        name.replace("<$variable>", value)
                    }
                    for ((kw, stepText) in outlineSteps) {
                        var expanded = stepText
                        for ((variable, value) in substitutions) {
                            expanded = expanded.replace("<$variable>", value)
                        }
                        allRawSteps.add(RawStep(kw, expanded, expandedOutlineName))
                    }
                }
                i++
                continue
            }

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
                if (inOutline) {
                    // Template steps go to outlineSteps; expanded rows added when Examples: is hit
                    outlineSteps.add(keyword to text)
                } else {
                    allRawSteps.add(RawStep(keyword, text, currentScenarioName))
                }
            }
            i++
        }

        // Deduplicate by normalised text
        val seen = mutableSetOf<String>()
        val unique = allSteps.filter { step ->
            val normalised = normalise(step.keyword, step.text)
            seen.add(normalised)
        }

        return ParsedFeature(unique, allRawSteps, allSteps)
    }

    /** Normalise for deduplication: lowercase, replace {placeholder}, <variable>, and "literal" with {}, trim. */
    fun normalise(keyword: String, text: String): String {
        var s = "$keyword $text".lowercase()
        s = s.replace(Regex("\"[^\"]*\""), "{}")   // "literal" and "<variable>" treated as placeholder
        s = s.replace(Regex("\\{[^}]+}"), "{}")
        s = s.replace(Regex("<[^>]+>"), "{}")
        // Replace standalone numbers (doubles first, then integers)
        s = s.replace(Regex("""(?<!\S)-?\d+\.\d+(?!\S)"""), "{}")
        s = s.replace(Regex("""(?<!\S)-?\d+(?!\S)"""), "{}")
        return s.trim()
    }

    private fun parseTableRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim() }.filter { it.isNotEmpty() }
}
