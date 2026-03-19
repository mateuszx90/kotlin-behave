package io.github.mcol.gherkin.parser

import io.github.mcol.gherkin.model.*

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

        fun flushScenario() {
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
                line.startsWith("Examples:") -> inExamples = true
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
                else -> stepKeyword(line)?.let { (kw, text) -> currentSteps.add(Step(kw, text)) }
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
