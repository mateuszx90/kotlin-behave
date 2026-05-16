package io.mcol.behave.ksp

/**
 * Lightweight raw-text Gherkin parser for the KSP processor.
 *
 * Unlike the runtime GherkinParser, this parser:
 * - Does NOT expand Scenario Outline rows — preserves `<variable>` tokens as-is
 * - Preserves the original step text with all placeholder/variable tokens intact
 * - Is used at compile time only, not at runtime
 * - Collects ALL parse errors (with line numbers) instead of stopping at the first one
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

    data class ParseError(
        val line: Int, // 1-indexed line number
        val message: String,
    )

    data class ParsedFeature(
        val steps: List<ParsedStep>, // all unique steps (deduplicated)
        val allStepInstances: List<RawStep>, // all concrete values preserved (for type validation)
        val allStepTemplates: List<ParsedStep> = emptyList(), // all steps before deduplication (for type unification)
        val errors: List<ParseError> = emptyList(), // accumulated parse errors
    ) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
    }

    private val keywords = listOf("Given", "When", "Then", "And", "But")

    /**
     * Resolve And/But to the previous Given/When/Then keyword.
     * In Gherkin, And/But are aliases for the last real keyword.
     */
    private fun resolveKeyword(keyword: String, lastRealKeyword: String): String = when (keyword) {
        "And", "But" -> lastRealKeyword.ifEmpty { keyword }
        else -> keyword
    }

    fun parse(featureText: String): ParsedFeature {
        val rawLines = featureText.lines()
        val lines = rawLines.map { it.trim() }
        val errors = mutableListOf<ParseError>()

        val featureLineIndex = lines.indexOfFirst { it.startsWith("Feature:") }
        if (featureLineIndex == -1) {
            // Point at first Scenario: or first step keyword, whichever comes first
            val anchorLine = lines.indexOfFirst { line ->
                line.startsWith("Scenario:") ||
                    line.startsWith("Background:") ||
                    keywords.any { line.startsWith("$it ") }
            }.takeIf { it >= 0 }?.plus(1) ?: 1
            errors.add(ParseError(anchorLine, "Missing Feature: declaration in feature file"))
            return ParsedFeature(emptyList(), emptyList(), emptyList(), errors)
        }

        val allSteps = mutableListOf<ParsedStep>()
        val allRawSteps = mutableListOf<RawStep>()
        var currentScenarioName: String? = null
        val featureIndent = rawLines[featureLineIndex].let { it.length - it.trimStart().length }
        var currentSectionIndent = featureIndent
        var inOrphanedGroup = false // true while consecutive orphaned steps are being skipped
        var inOutline = false
        var outlineName = ""
        var outlineSteps = mutableListOf<Pair<String, String>>() // keyword, text
        var lastRealKeyword = "" // tracks last Given/When/Then for And/But resolution

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val lineIndent = rawLines[i].length - rawLines[i].trimStart().length
            val lineNumber = i + 1

            // Track current scenario/background name and outline context
            when {
                line.startsWith("Scenario Outline:") -> {
                    inOutline = true
                    inOrphanedGroup = false
                    outlineName = line.removePrefix("Scenario Outline:").trim()
                    outlineSteps = mutableListOf()
                    currentScenarioName = outlineName
                    currentSectionIndent = lineIndent
                    lastRealKeyword = ""
                }
                line.startsWith("Scenario:") -> {
                    inOutline = false
                    inOrphanedGroup = false
                    currentScenarioName = line.removePrefix("Scenario:").trim()
                    currentSectionIndent = lineIndent
                    lastRealKeyword = ""
                }
                line.startsWith("Background:") -> {
                    inOutline = false
                    inOrphanedGroup = false
                    currentScenarioName = "Background"
                    currentSectionIndent = lineIndent
                    lastRealKeyword = ""
                }
            }

            // Expand Scenario Outline Examples into allRawSteps
            if (line.startsWith("Examples:")) {
                expandExamples(lines, i, outlineSteps, outlineName, allRawSteps)
                i++
                continue
            }

            val rawKeyword = keywords.firstOrNull { line.startsWith("$it ") }
            if (rawKeyword != null) {
                val text = line.removePrefix("$rawKeyword ").trim()
                if (currentScenarioName == null || lineIndent <= currentSectionIndent) {
                    if (!inOrphanedGroup) {
                        errors.add(
                            ParseError(lineNumber, "Step '$rawKeyword $text' found outside any Scenario or Background"),
                        )
                        inOrphanedGroup = true
                    }
                    i++
                    continue // skip orphaned step, keep collecting errors
                }
                inOrphanedGroup = false
                val resolved = resolveKeyword(rawKeyword, lastRealKeyword)
                if (rawKeyword in listOf("Given", "When", "Then")) {
                    lastRealKeyword = rawKeyword
                }
                val (hasTable, tableColumns) = readDataTableAhead(lines, i + 1)
                allSteps.add(ParsedStep(resolved, text, hasTable, tableColumns))
                if (inOutline) {
                    outlineSteps.add(resolved to text)
                } else {
                    allRawSteps.add(RawStep(resolved, text, currentScenarioName ?: ""))
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

        return ParsedFeature(unique, allRawSteps, allSteps, errors)
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

    private fun expandExamples(
        lines: List<String>,
        startIndex: Int,
        outlineSteps: List<Pair<String, String>>,
        outlineName: String,
        allRawSteps: MutableList<RawStep>,
    ) {
        var j = startIndex + 1
        var header = emptyList<String>()
        val dataRows = mutableListOf<List<String>>()
        while (j < lines.size) {
            val next = lines[j].trim()
            when {
                next.isBlank() || next.startsWith("#") -> j++
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
            val expandedName = substitutions.entries.fold(outlineName) { name, (variable, value) ->
                name.replace("<$variable>", value)
            }
            for ((kw, stepText) in outlineSteps) {
                val expanded = substitutions.entries.fold(stepText) { s, (variable, value) ->
                    s.replace("<$variable>", value)
                }
                allRawSteps.add(RawStep(kw, expanded, expandedName))
            }
        }
    }

    private fun readDataTableAhead(lines: List<String>, startFrom: Int): Pair<Boolean, List<String>> {
        var hasTable = false
        val tableColumns = mutableListOf<String>()
        var j = startFrom
        while (j < lines.size) {
            val next = lines[j].trim()
            when {
                next.isBlank() || next.startsWith("#") -> j++
                next.startsWith("|") -> {
                    if (!hasTable) {
                        tableColumns.addAll(parseTableRow(next))
                        hasTable = true
                    }
                    j++
                }
                else -> break
            }
        }
        return hasTable to tableColumns
    }

    private fun parseTableRow(line: String): List<String> = line
        .trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
