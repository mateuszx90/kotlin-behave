package io.mcol.behave.runner

/**
 * Renders [RunResult]s as JUnit XML — the format CI servers and dashboards consume. Each feature
 * becomes a `<testsuite>` and each scenario a `<testcase>`; failures carry the error/message,
 * pending and tag-skipped scenarios are `<skipped>`. Pure (no IO) — write the string yourself.
 */
object JUnitXmlReport {
    /** A `<testsuites>` document wrapping one `<testsuite>` per feature. */
    fun render(results: List<RunResult>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("<testsuites>")
        for (result in results) append(renderSuite(result, indent = "  "))
        append("</testsuites>")
    }

    /** A single `<testsuite>` for one feature. */
    fun renderSuite(result: RunResult, indent: String = ""): String = buildString {
        val tests = result.scenarios.size
        val failures = result.scenarios.count { !it.passed && !it.pending && !it.skipped }
        val skipped = result.scenarios.count { it.skipped || it.pending }
        appendLine(
            """$indent<testsuite name="${attr(result.featureName)}" tests="$tests" failures="$failures" skipped="$skipped">""",
        )
        for (s in result.scenarios) append(renderCase(s, result.featureName, "$indent  "))
        appendLine("$indent</testsuite>")
    }

    private fun renderCase(s: ScenarioResult, feature: String, indent: String): String = buildString {
        val open = """$indent<testcase classname="${attr(feature)}" name="${attr(s.name)}">"""
        when {
            s.skipped -> {
                appendLine(open)
                appendLine("""$indent  <skipped message="filtered out by tags"/>""")
                appendLine("$indent</testcase>")
            }
            s.pending -> {
                appendLine(open)
                appendLine("""$indent  <skipped message="pending at step: ${attr(s.failedStep ?: "")}"/>""")
                appendLine("$indent</testcase>")
            }
            s.passed -> appendLine("""$indent<testcase classname="${attr(feature)}" name="${attr(s.name)}"/>""")
            else -> {
                appendLine(open)
                val msg = attr("Failed at step: ${s.failedStep ?: "?"}")
                appendLine("""$indent  <failure message="$msg">${text(s.error?.message ?: "")}</failure>""")
                appendLine("$indent</testcase>")
            }
        }
    }

    /** Escape for an XML attribute value. */
    private fun attr(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("\n", "&#10;")

    /** Escape for XML element text. */
    private fun text(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
