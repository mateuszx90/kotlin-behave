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
        val keyword: String, // "Given" | "When" | "Then" | "And" | "But"
        val text: String, // step text with {placeholders} and <variables> preserved
        val hasDataTable: Boolean, // true if a | table follows this step
        val tableColumns: List<String>, // column headers if DataTable present
    )

    data class RawStep(
        val keyword: String, // "Given" | "When" | "Then" | "And" | "But"
        val text: String, // original text before normalization (concrete values intact)
        val scenarioName: String, // for error reporting: which scenario this step appears in
    )

    data class ParsedFeature(
        val steps: List<ParsedStep>, // all unique steps (deduplicated)
        val allStepInstances: List<RawStep>, // all concrete values preserved (for type validation)
        val allStepTemplates: List<ParsedStep> = emptyList(), // all steps before deduplication (for type unification)
    )

    private val keywords = listOf("Given", "When", "Then", "And", "But")
    private val sectionKeywords = listOf("Feature:", "Background:", "Scenario:", "Scenario Outline:", "Examples:")

    /**
     * Resolve And/But to the previous Given/When/Then keyword.
     * In Gherkin, And/But are aliases for the last real keyword.
     */
    private fun resolveKeyword(keyword: String, lastRealKeyword: String): String = when (keyword) {
        "And", "But" -> lastRealKeyword.ifEmpty { keyword }
        else -> keyword
    }

    fun parse(featureText: String): ParsedFeature {
        val lines = featureText.lines().map { it.trim() }
        val allSteps = mutableListOf<ParsedStep>()
        val allRawSteps = mutableListOf<RawStep>()
        var currentScenarioName = ""
        var inOutline = false
        var outlineName = ""
        var outlineSteps = mutableListOf<Pair<String, String>>() // keyword, text
        var lastRealKeyword = "" // tracks last Given/When/Then for And/But resolution

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
                    lastRealKeyword = ""
                }
                line.startsWith("Scenario:") -> {
                    inOutline = false
                    currentScenarioName = line.removePrefix("Scenario:").trim()
                    lastRealKeyword = ""
                }
                line.startsWith("Background:") -> {
                    inOutline = false
                    currentScenarioName = "Background"
                    lastRealKeyword = ""
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
                        next.isBlank() || next.startsWith("#") -> {
                            j++
                            continue
                        }
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
                    val expandedOutlineName =
                        substitutions.entries.fold(outlineName) { name, (variable, value) ->
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

            val rawKeyword = keywords.firstOrNull { line.startsWith("$it ") }
            if (rawKeyword != null) {
                val resolved = resolveKeyword(rawKeyword, lastRealKeyword)
                if (rawKeyword in listOf("Given", "When", "Then")) {
                    lastRealKeyword = rawKeyword
                }
                val text = line.removePrefix("$rawKeyword ").trim()
                // Look ahead for DataTable
                var hasTable = false
                val tableColumns = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size) {
                    val next = lines[j].trim()
                    when {
                        next.isBlank() || next.startsWith("#") -> {
                            j++
                            continue
                        }
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
                allSteps.add(ParsedStep(resolved, text, hasTable, tableColumns))
                if (inOutline) {
                    // Template steps go to outlineSteps; expanded rows added when Examples: is hit
                    outlineSteps.add(resolved to text)
                } else {
                    allRawSteps.add(RawStep(resolved, text, currentScenarioName))
                }
            }
            i++
        }

        // Deduplicate by normalised text
        val seen = mutableSetOf<String>()
        val unique =
            allSteps.filter { step ->
                val normalised = normalise(step.keyword, step.text)
                seen.add(normalised)
            }

        return ParsedFeature(unique, allRawSteps, allSteps)
    }

    /** Normalise for deduplication: lowercase, replace {placeholder}, <variable>, and "literal" with {}, trim.
     *  Keyword is excluded — And/But are already resolved, and Given/When/Then with same text should deduplicate. */
    fun normalise(
        keyword: String,
        text: String,
    ): String {
        var s = text.lowercase()
        s = s.replace(Regex("\"[^\"]*\""), "{}") // "literal" and "<variable>" treated as placeholder
        s = s.replace(Regex("\\{[^}]+}"), "{}")
        s = s.replace(Regex("<[^>]+>"), "{}")
        // Replace standalone numbers (doubles first, then integers)
        s = s.replace(Regex("""(?<!\S)-?\d+\.\d+(?!\S)"""), "{}")
        s = s.replace(Regex("""(?<!\S)-?\d+(?!\S)"""), "{}")
        return s.trim()
    }

    private fun parseTableRow(line: String): List<String> = line
        .trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
