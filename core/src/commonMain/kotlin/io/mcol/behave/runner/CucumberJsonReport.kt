package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Step

/**
 * Renders run results as the classic Cucumber JSON format — the schema consumed by the
 * cucumber-html-reporter, Allure's cucumber-json plugin and the broader Cucumber ecosystem.
 *
 * The document is a JSON array of feature objects; each scenario becomes an `element` and each
 * step (Background first, then the scenario's own) a `step` with a `result.status` of
 * `passed` / `failed` / `skipped` / `pending`. Pure (no IO) — write the string yourself.
 *
 * Unlike [JUnitXmlReport] this needs the parsed [Feature] (for the step list), so callers pair
 * each [RunResult] with the [Feature] it came from via [FeatureRun].
 */
object CucumberJsonReport {
    /** One feature's parsed model paired with the result of running it (+ an optional source uri). */
    data class FeatureRun(
        val feature: Feature,
        val result: RunResult,
        val uri: String = "",
    )

    private const val PASSED = "passed"
    private const val FAILED = "failed"
    private const val SKIPPED = "skipped"
    private const val PENDING = "pending"

    /** The full document: a JSON array of feature objects. */
    fun render(runs: List<FeatureRun>): String = runs.joinToString(",", prefix = "[", postfix = "]") { renderFeature(it) }

    /** A single feature object. */
    fun renderFeature(run: FeatureRun): String {
        val feature = run.feature
        val slug = slug(feature.name)
        val uri = run.uri.ifBlank { "$slug.feature" }
        val line = LineCounter()
        val featureLine = line.next()
        val backgroundSteps = feature.background?.steps ?: emptyList()
        val elements = feature.scenarios.joinToString(",") { scenario ->
            val result = run.result.scenarios.firstOrNull { it.name == scenario.name }
            renderElement(scenario, backgroundSteps, result, slug, line)
        }
        return buildString {
            append("{")
            append("\"uri\":").append(str(uri)).append(",")
            append("\"id\":").append(str(slug)).append(",")
            append("\"keyword\":\"Feature\",")
            append("\"name\":").append(str(feature.name)).append(",")
            append("\"line\":").append(featureLine).append(",")
            append("\"description\":\"\",")
            append("\"tags\":").append(renderTags(feature.tags, featureLine)).append(",")
            append("\"elements\":[").append(elements).append("]")
            append("}")
        }
    }

    private fun renderElement(
        scenario: io.mcol.behave.model.Scenario,
        backgroundSteps: List<Step>,
        result: ScenarioResult?,
        featureSlug: String,
        line: LineCounter,
    ): String {
        val elementLine = line.next()
        val allSteps = backgroundSteps + scenario.steps
        val statuses = statusesFor(allSteps, result)
        val stepsJson = allSteps.mapIndexed { i, step ->
            renderStep(step, statuses[i], line)
        }.joinToString(",")
        return buildString {
            append("{")
            append("\"id\":").append(str("$featureSlug;${slug(scenario.name)}")).append(",")
            append("\"keyword\":\"Scenario\",")
            append("\"type\":\"scenario\",")
            append("\"name\":").append(str(scenario.name)).append(",")
            append("\"line\":").append(elementLine).append(",")
            append("\"tags\":").append(renderTags(scenario.tags, elementLine)).append(",")
            append("\"steps\":[").append(stepsJson).append("]")
            append("}")
        }
    }

    private fun renderStep(step: Step, status: StepStatus, line: LineCounter): String = buildString {
        append("{")
        append("\"keyword\":").append(str(cucumberKeyword(step.keyword))).append(",")
        append("\"name\":").append(str(step.text)).append(",")
        append("\"line\":").append(line.next()).append(",")
        append("\"result\":{")
        append("\"status\":").append(str(status.status))
        append(",\"duration\":0")
        if (status.errorMessage != null) {
            append(",\"error_message\":").append(str(status.errorMessage))
        }
        append("}")
        append("}")
    }

    private data class StepStatus(val status: String, val errorMessage: String? = null)

    /**
     * Walk the scenario's steps (Background first) and assign a cucumber status to each, derived
     * from the coarse [ScenarioResult]: steps up to the failing one are `passed`, the failing /
     * pending step carries that status (and any error message), and following steps are `skipped`.
     */
    private fun statusesFor(steps: List<Step>, result: ScenarioResult?): List<StepStatus> {
        if (result == null || result.skipped) return steps.map { StepStatus(SKIPPED) }
        if (result.passed) return steps.map { StepStatus(PASSED) }

        val rawIdx = steps.indexOfFirst { it.text == result.failedStep }
        val failIdx = if (rawIdx >= 0) rawIdx else 0
        val failStatus = if (result.pending) PENDING else FAILED
        return steps.mapIndexed { i, _ ->
            when {
                i < failIdx -> StepStatus(PASSED)
                i == failIdx -> StepStatus(failStatus, if (failStatus == FAILED) result.error?.message else null)
                else -> StepStatus(SKIPPED)
            }
        }
    }

    private fun renderTags(tags: Set<String>, line: Int): String = tags.joinToString(",", prefix = "[", postfix = "]") { tag ->
        """{"name":${str(tag)},"line":$line}"""
    }

    private fun cucumberKeyword(keyword: Keyword): String = when (keyword) {
        Keyword.GIVEN -> "Given "
        Keyword.WHEN -> "When "
        Keyword.THEN -> "Then "
        Keyword.AND -> "And "
        Keyword.BUT -> "But "
    }

    /** A lowercase, hyphen-separated id, mirroring cucumber's slugging of feature/scenario names. */
    private fun slug(name: String): String = name
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    /** Monotonic line-number source — the model carries no source lines, so we synthesize them. */
    private class LineCounter(private var value: Int = 0) {
        fun next(): Int = ++value
    }

    /** JSON string literal with the mandatory escapes. */
    private fun str(s: String): String = buildString {
        append('"')
        for (ch in s) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u" + ch.code.toString(16).padStart(4, '0')) else append(ch)
            }
        }
        append('"')
    }
}
