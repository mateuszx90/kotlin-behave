package io.mcol.behave.parser

import io.mcol.behave.model.*

object GherkinParser {

    // Keyword synonyms (Gherkin spec): "Scenario Template:"≡"Scenario Outline:",
    // "Example:"≡"Scenario:", "Scenarios:"≡"Examples:". Kept as helpers so [parse] stays simple.
    private fun isOutlineStart(l: String) = l.startsWith("Scenario Outline:") || l.startsWith("Scenario Template:")

    private fun isScenarioStart(l: String) = l.startsWith("Scenario:") || l.startsWith("Example:")

    private fun isExamplesStart(l: String) = l.startsWith("Examples:") || l.startsWith("Scenarios:")

    private fun isRuleStart(l: String) = l.startsWith("Rule:")

    /**
     * Read a Doc String that opens at [openIndex] (a line whose trimmed form starts with `"""` or
     * ` ``` `). Returns the de-indented content (lines joined by `\n`, comments/blank lines kept
     * verbatim) and the index of the line AFTER the closing fence. The opening fence's column sets
     * how much leading whitespace is stripped from each content line (Gherkin indentation rule).
     */
    internal fun extractDocString(rawLines: List<String>, openIndex: Int): Pair<String, Int> {
        val openRaw = rawLines[openIndex]
        val fence = if (openRaw.trim().startsWith("```")) "```" else "\"\"\""
        val indent = openRaw.indexOf(fence).coerceAtLeast(0)
        val content = mutableListOf<String>()
        var i = openIndex + 1
        while (i < rawLines.size && rawLines[i].trim() != fence) {
            val l = rawLines[i]
            var k = 0
            while (k < indent && k < l.length && l[k].isWhitespace()) k++
            content.add(l.substring(k))
            i++
        }
        val next = if (i < rawLines.size) i + 1 else i // skip closing fence when present
        return content.joinToString("\n") to next
    }

    fun parse(input: String): Feature {
        // Keep raw lines: comments/blank lines are skipped inline so they are NOT stripped from
        // inside a Doc String (where `#` and blank lines are literal content).
        val rawLines = input.lines()

        var featureName = ""
        var featureTags = emptySet<String>()
        var background: Background? = null
        val scenarios = mutableListOf<Scenario>()

        var currentScenarioName: String? = null
        var currentScenarioTags = emptySet<String>()
        var isOutline = false
        var isBackground = false
        var currentSteps = mutableListOf<Step>()
        var exampleHeaders = listOf<String>()
        var exampleTags = emptySet<String>()
        var inExamples = false
        var pendingTags = emptySet<String>()

        // DataTable tracking: accumulate table rows for the most recently seen step
        var pendingStep: Step? = null
        var tableHeaders: List<String> = emptyList()
        var tableRows: MutableList<Map<String, String?>> = mutableListOf()

        // Rule (Gherkin 6+): scenarios under a Rule also run the Rule's own Background, in addition
        // to the feature Background. We bake the Rule Background into each Rule scenario's steps; the
        // feature Background is still prepended by the runner, giving feature-bg → rule-bg → steps.
        var inRule = false
        var collectingRuleBackground = false
        var ruleBackground = listOf<Step>()

        fun parseTags(line: String): Set<String> = line.trim().split("\\s+".toRegex()).filter { it.startsWith("@") }.toSet()

        fun flushPendingStep() {
            pendingStep?.let { step ->
                val final = if (tableRows.isNotEmpty()) step.copy(dataTable = DataTable(tableRows.toList())) else step
                currentSteps.add(final)
            }
            pendingStep = null
            tableHeaders = emptyList()
            tableRows = mutableListOf()
        }

        fun flushScenario() {
            flushPendingStep()
            if (isBackground && collectingRuleBackground) {
                ruleBackground = currentSteps.toList()
            } else if (isBackground) {
                background = Background(currentSteps.toList())
            } else if (currentScenarioName != null && !isOutline) {
                val finalTags = featureTags + currentScenarioTags
                val steps = if (inRule) ruleBackground + currentSteps else currentSteps.toList()
                scenarios.add(Scenario(currentScenarioName!!, steps, tags = finalTags))
            }
            collectingRuleBackground = false
            currentSteps = mutableListOf()
            currentScenarioName = null
            currentScenarioTags = emptySet()
            isOutline = false
            isBackground = false
            exampleHeaders = listOf()
            exampleTags = emptySet()
            inExamples = false
        }

        fun parseTableRow(line: String): List<String> = io.mcol.behave.gherkin.GherkinTable.splitRow(line)

        var li = 0
        while (li < rawLines.size) {
            val line = rawLines[li].trim()
            // A Doc String ("""... or ```...) attaches its content to the preceding step.
            if (pendingStep != null && (line.startsWith("\"\"\"") || line.startsWith("```"))) {
                val (doc, next) = extractDocString(rawLines, li)
                pendingStep = pendingStep?.copy(docString = doc)
                li = next
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) {
                li++
                continue
            }
            when {
                line.startsWith("@") -> pendingTags = parseTags(line)
                line.startsWith("Feature:") -> {
                    featureTags = pendingTags
                    pendingTags = emptySet()
                    featureName = line.removePrefix("Feature:").trim()
                }
                isRuleStart(line) -> {
                    flushScenario()
                    inRule = true
                    ruleBackground = emptyList()
                    pendingTags = emptySet()
                }
                line.startsWith("Background:") -> {
                    flushScenario()
                    isBackground = true
                    collectingRuleBackground = inRule
                    pendingTags = emptySet()
                }
                isOutlineStart(line) -> {
                    flushScenario()
                    currentScenarioTags = pendingTags
                    pendingTags = emptySet()
                    currentScenarioName = line.substringAfter(':').trim()
                    isOutline = true
                }
                isScenarioStart(line) -> {
                    flushScenario()
                    currentScenarioTags = pendingTags
                    pendingTags = emptySet()
                    currentScenarioName = line.substringAfter(':').trim()
                }
                isExamplesStart(line) -> {
                    flushPendingStep()
                    exampleTags = pendingTags
                    pendingTags = emptySet()
                    inExamples = true
                }
                inExamples && line.startsWith("|") -> {
                    val cells = parseTableRow(line)
                    if (exampleHeaders.isEmpty()) {
                        exampleHeaders = cells
                    } else {
                        val row = exampleHeaders.zip(cells).toMap()
                        val resolvedSteps = currentSteps.map { step ->
                            step.copy(text = row.entries.fold(step.text) { t, (k, v) -> t.replace("<$k>", v) })
                        }
                        val rowLabel = row.entries.joinToString(", ") { (k, v) -> "$k=$v" }
                        val finalTags = featureTags + currentScenarioTags + exampleTags
                        val finalSteps = if (inRule) ruleBackground + resolvedSteps else resolvedSteps
                        scenarios.add(Scenario("$currentScenarioName [$rowLabel]", finalSteps, listOf(row), finalTags))
                    }
                }
                else -> {
                    val kwText = stepKeyword(line)
                    if (kwText != null) {
                        flushPendingStep()
                        pendingStep = Step(kwText.first, kwText.second)
                    } else if (line.startsWith("|") && pendingStep != null && !inExamples) {
                        val cells = parseTableRow(line)
                        if (tableHeaders.isEmpty()) {
                            tableHeaders = cells
                        } else {
                            tableRows.add(tableHeaders.zip(cells.map { if (it == "null") null else it }).toMap())
                        }
                    }
                }
            }
            li++
        }
        flushScenario()

        return Feature(featureName, background, scenarios, featureTags)
    }

    internal fun stepKeyword(line: String): Pair<Keyword, String>? {
        val prefixes = listOf(
            "Given" to Keyword.GIVEN,
            "When" to Keyword.WHEN,
            "Then" to Keyword.THEN,
            "And" to Keyword.AND,
            "But" to Keyword.BUT,
            // The `*` bullet is a generic step keyword; matching is by text so AND is a neutral label.
            "*" to Keyword.AND,
        )
        for ((prefix, kw) in prefixes) {
            if (line.startsWith("$prefix ")) return kw to line.removePrefix("$prefix ").trim()
        }
        return null
    }
}
