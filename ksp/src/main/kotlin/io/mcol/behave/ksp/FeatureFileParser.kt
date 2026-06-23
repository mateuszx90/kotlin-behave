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
        val hasDocString: Boolean = false, // true if a """ / ``` doc string follows this step
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
        val allStepInstances: List<RawStep>, // all concrete values preserved (for type validation + inference)
        val allStepTemplates: List<ParsedStep> = emptyList(), // all steps before deduplication (for type unification)
        val errors: List<ParseError> = emptyList(), // accumulated parse errors
    ) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
    }

    private val keywords = listOf("Given", "When", "Then", "And", "But", "*")

    /**
     * Resolve And/But/`*` to the previous Given/When/Then keyword.
     * In Gherkin, And/But and the `*` bullet are aliases for the last real keyword.
     */
    private fun resolveKeyword(keyword: String, lastRealKeyword: String): String = when (keyword) {
        "And", "But", "*" -> lastRealKeyword.ifEmpty { keyword }
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
        var outlineLineNumber = 0
        var outlineHadExamples = false
        var outlineSteps = mutableListOf<Pair<String, String>>() // keyword, text
        var lastRealKeyword = "" // tracks last Given/When/Then for And/But resolution

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val lineIndent = rawLines[i].length - rawLines[i].trimStart().length
            val lineNumber = i + 1

            // Track current scenario/background name and outline context
            when {
                line.startsWith("Scenario Outline:") || line.startsWith("Scenario Template:") -> {
                    missingExamplesErrorIfNeeded(inOutline, outlineHadExamples, outlineSteps, outlineLineNumber, outlineName)
                        ?.let { errors.add(it) }
                    val keyword = if (line.startsWith("Scenario Outline:")) "Scenario Outline:" else "Scenario Template:"
                    outlineName = line.removePrefix(keyword).trim()
                    if (outlineName.isEmpty()) {
                        errors.add(ParseError(lineNumber, "$keyword declaration is missing a name"))
                    }
                    inOutline = true
                    outlineHadExamples = false
                    inOrphanedGroup = false
                    outlineLineNumber = lineNumber
                    outlineSteps = mutableListOf()
                    currentScenarioName = outlineName.ifEmpty { keyword.trimEnd(':') }
                    currentSectionIndent = lineIndent
                    lastRealKeyword = ""
                }
                line.startsWith("Scenario:") || line.startsWith("Example:") -> {
                    missingExamplesErrorIfNeeded(inOutline, outlineHadExamples, outlineSteps, outlineLineNumber, outlineName)
                        ?.let { errors.add(it) }
                    inOutline = false
                    outlineHadExamples = false
                    inOrphanedGroup = false
                    currentScenarioName = line.substringAfter(':').trim()
                    currentSectionIndent = lineIndent
                    lastRealKeyword = ""
                }
                line.startsWith("Background:") -> {
                    missingExamplesErrorIfNeeded(inOutline, outlineHadExamples, outlineSteps, outlineLineNumber, outlineName)
                        ?.let { errors.add(it) }
                    inOutline = false
                    outlineHadExamples = false
                    inOrphanedGroup = false
                    currentScenarioName = "Background"
                    currentSectionIndent = lineIndent
                    lastRealKeyword = ""
                }
            }

            // Expand Scenario Outline Examples into allRawSteps ("Scenarios:" is a synonym)
            if (line.startsWith("Examples:") || line.startsWith("Scenarios:")) {
                outlineHadExamples = true
                expandExamples(lines, i, outlineSteps, outlineName, allRawSteps, errors, lineNumber)
                i++
                continue
            }

            // Doc String block: mark the preceding step and skip the fenced content so its lines
            // are not mis-parsed as steps/tables.
            if (line.startsWith("\"\"\"") || line.startsWith("```")) {
                if (allSteps.isNotEmpty()) {
                    allSteps[allSteps.lastIndex] = allSteps.last().copy(hasDocString = true)
                }
                val fence = if (line.startsWith("```")) "```" else "\"\"\""
                i++
                while (i < lines.size && lines[i] != fence) i++
                if (i < lines.size) i++ // skip closing fence
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

        // Check last outline in file
        missingExamplesErrorIfNeeded(inOutline, outlineHadExamples, outlineSteps, outlineLineNumber, outlineName)
            ?.let { errors.add(it) }

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
        errors: MutableList<ParseError>,
        examplesLineNumber: Int,
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

        val variables = outlineSteps
            .flatMap { (_, text) -> Regex("<([^>]+)>").findAll(text).map { it.groupValues[1] } }
            .distinct()
        val missing = variables.filter { it !in header }
        if (missing.isNotEmpty()) {
            errors.add(
                ParseError(
                    examplesLineNumber,
                    "Examples: table for Scenario Outline '$outlineName' is missing columns for: " +
                        missing.joinToString { "<$it>" },
                ),
            )
            return
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

    private fun missingExamplesErrorIfNeeded(
        inOutline: Boolean,
        hadExamples: Boolean,
        steps: List<Pair<String, String>>,
        lineNumber: Int,
        name: String,
    ): ParseError? = if (inOutline && !hadExamples) {
        val variables = steps
            .flatMap { (_, text) -> Regex("<([^>]+)>").findAll(text).map { it.groupValues[1] } }
            .distinct()
        val suggestion = buildString {
            appendLine()
            appendLine("    Examples:")
            if (variables.isNotEmpty()) {
                append("      | ${variables.joinToString(" | ")} |")
                appendLine()
                append("      | ${variables.joinToString(" | ") { "value" }} |")
            } else {
                appendLine("      | |")
                append("      | |")
            }
        }
        ParseError(lineNumber, "Scenario Outline '$name' has no Examples: block. Add:$suggestion")
    } else {
        null
    }

    // Split a `| a | b |` row into cells. The leading/trailing border pipes are stripped first, so
    // empty cells (`| a |  | c |`) are PRESERVED — dropping them would misalign columns with headers.
    // Cells may escape the delimiter and special chars: `\|` -> `|`, `\\` -> `\`, `\n` -> newline.
    private fun parseTableRow(line: String): List<String> {
        val inner = line.trim().removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            when {
                c == '\\' && i + 1 < inner.length -> {
                    when (inner[i + 1]) {
                        '|' -> sb.append('|')
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        else -> sb.append(c).append(inner[i + 1])
                    }
                    i += 2
                }
                c == '|' -> {
                    cells.add(sb.toString().trim())
                    sb.clear()
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        cells.add(sb.toString().trim())
        return cells
    }
}
