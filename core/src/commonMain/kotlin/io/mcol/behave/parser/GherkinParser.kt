package io.mcol.behave.parser

import io.mcol.behave.model.*

object GherkinParser {

    // Keyword synonyms (Gherkin spec): "Scenario Template:"≡"Scenario Outline:",
    // "Example:"≡"Scenario:", "Scenarios:"≡"Examples:". Kept as helpers so [parse] stays simple.
    private fun isOutlineStart(l: String) = l.startsWith("Scenario Outline:") || l.startsWith("Scenario Template:")

    private fun isScenarioStart(l: String) = l.startsWith("Scenario:") || l.startsWith("Example:")

    private fun isExamplesStart(l: String) = l.startsWith("Examples:") || l.startsWith("Scenarios:")

    fun parse(input: String): Feature {
        val lines = input.lines()
            .map { it.trim() }
            .filterNot { it.startsWith("#") || it.isBlank() }

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
            if (isBackground) {
                background = Background(currentSteps.toList())
            } else if (currentScenarioName != null && !isOutline) {
                val finalTags = featureTags + currentScenarioTags
                scenarios.add(Scenario(currentScenarioName!!, currentSteps.toList(), tags = finalTags))
            }
            currentSteps = mutableListOf()
            currentScenarioName = null
            currentScenarioTags = emptySet()
            isOutline = false
            isBackground = false
            exampleHeaders = listOf()
            exampleTags = emptySet()
            inExamples = false
        }

        fun parseTableRow(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim() }

        for (line in lines) {
            when {
                line.startsWith("@") -> pendingTags = parseTags(line)
                line.startsWith("Feature:") -> {
                    featureTags = pendingTags
                    pendingTags = emptySet()
                    featureName = line.removePrefix("Feature:").trim()
                }
                line.startsWith("Background:") -> {
                    flushScenario()
                    isBackground = true
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
                        scenarios.add(Scenario("$currentScenarioName [$rowLabel]", resolvedSteps, listOf(row), finalTags))
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
        )
        for ((prefix, kw) in prefixes) {
            if (line.startsWith("$prefix ")) return kw to line.removePrefix("$prefix ").trim()
        }
        return null
    }
}
