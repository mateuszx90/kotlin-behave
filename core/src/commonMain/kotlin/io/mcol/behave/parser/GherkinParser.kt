package io.mcol.behave.parser

import io.mcol.behave.model.*

object GherkinParser {

    fun parse(input: String): Feature {
        val lines = input.lines()
            .map { it.trim() }
            .filterNot { it.startsWith("#") || it.isBlank() }

        var featureName = ""
        var background: Background? = null
        val scenarios = mutableListOf<Scenario>()

        var currentScenarioName: String? = null
        var isOutline = false
        var isBackground = false
        var currentSteps = mutableListOf<Step>()
        var exampleHeaders = listOf<String>()
        var inExamples = false

        // DataTable tracking: accumulate table rows for the most recently seen step
        var pendingStep: Step? = null
        var tableHeaders: List<String> = emptyList()
        var tableRows: MutableList<Map<String, String?>> = mutableListOf()

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
                scenarios.add(Scenario(currentScenarioName!!, currentSteps.toList()))
            }
            currentSteps = mutableListOf()
            currentScenarioName = null
            isOutline = false
            isBackground = false
            exampleHeaders = listOf()
            inExamples = false
        }

        fun parseTableRow(line: String): List<String> =
            line.trim().removePrefix("|").removeSuffix("|")
                .split("|").map { it.trim() }

        for (line in lines) {
            when {
                line.startsWith("Feature:") -> featureName = line.removePrefix("Feature:").trim()
                line.startsWith("Background:") -> { flushScenario(); isBackground = true }
                line.startsWith("Scenario Outline:") -> {
                    flushScenario()
                    currentScenarioName = line.removePrefix("Scenario Outline:").trim()
                    isOutline = true
                }
                line.startsWith("Scenario:") -> {
                    flushScenario()
                    currentScenarioName = line.removePrefix("Scenario:").trim()
                }
                line.startsWith("Examples:") -> { flushPendingStep(); inExamples = true }
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
                        scenarios.add(Scenario("$currentScenarioName [$rowLabel]", resolvedSteps, listOf(row)))
                    }
                }
                else -> {
                    val kwText = stepKeyword(line)
                    if (kwText != null) {
                        flushPendingStep()  // commit previous step (with any accumulated table rows)
                        pendingStep = Step(kwText.first, kwText.second)
                    } else if (line.startsWith("|") && pendingStep != null && !inExamples) {
                        val cells = parseTableRow(line)
                        if (tableHeaders.isEmpty()) tableHeaders = cells
                        else tableRows.add(tableHeaders.zip(cells.map { if (it == "null") null else it }).toMap())
                    }
                }
            }
        }
        flushScenario()

        return Feature(featureName, background, scenarios)
    }

    internal fun stepKeyword(line: String): Pair<Keyword, String>? {
        val prefixes = listOf(
            "Given" to Keyword.GIVEN,
            "When"  to Keyword.WHEN,
            "Then"  to Keyword.THEN,
            "And"   to Keyword.AND,
            "But"   to Keyword.BUT,
        )
        for ((prefix, kw) in prefixes) {
            if (line.startsWith("$prefix ")) return kw to line.removePrefix("$prefix ").trim()
        }
        return null
    }
}
