package io.github.mcol.gherkin.parser

import io.github.mcol.gherkin.model.*

object GherkinParser {

    fun parse(input: String): Feature {
        val lines = input.lines()
            .map { it.trim() }
            .filterNot { it.startsWith("#") || it.isBlank() }

        var featureName = ""
        val scenarios = mutableListOf<Scenario>()
        var background: Background? = null

        var currentScenarioName: String? = null
        var currentSteps = mutableListOf<Step>()
        var isBackground = false

        fun flushCurrent() {
            if (isBackground) {
                background = Background(currentSteps.toList())
            } else if (currentScenarioName != null) {
                scenarios.add(Scenario(currentScenarioName!!, currentSteps.toList()))
            }
            currentSteps = mutableListOf()
            currentScenarioName = null
            isBackground = false
        }

        for (line in lines) {
            when {
                line.startsWith("Feature:") -> featureName = line.removePrefix("Feature:").trim()
                line.startsWith("Background:") -> { flushCurrent(); isBackground = true }
                line.startsWith("Scenario:") -> {
                    flushCurrent()
                    currentScenarioName = line.removePrefix("Scenario:").trim()
                }
                else -> stepKeyword(line)?.let { (kw, text) -> currentSteps.add(Step(kw, text)) }
            }
        }
        flushCurrent()

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
