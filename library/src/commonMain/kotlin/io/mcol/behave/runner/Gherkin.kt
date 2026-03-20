package io.mcol.behave.runner

import io.mcol.behave.parser.GherkinParser
import io.mcol.behave.steps.StepDefinitions

fun <C> gherkin(path: String, steps: StepDefinitions<C>) {
    val content = readResource(path)
    val feature = GherkinParser.parse(content)
    val result = GherkinRunner(steps).run(feature)
    if (result.hasFailures) {
        val messages = result.scenarios
            .filter { !it.passed && !it.pending }
            .joinToString("\n") { "  [${it.name}] ${it.error?.message ?: it.failedStep}" }
        throw AssertionError("${result.scenarios.count { !it.passed && !it.pending }} scenario(s) failed:\n$messages")
    }
}

fun <C> gherkin(
    path: String,
    steps: StepDefinitions<C>,
    runScenario: (ctx: C, run: () -> Unit) -> Unit,
) {
    val content = readResource(path)
    val feature = GherkinParser.parse(content)
    val result = GherkinRunner(steps).runWithPerScenarioRunner(feature, runScenario)
    if (result.hasFailures) {
        val messages = result.scenarios
            .filter { !it.passed && !it.pending }
            .joinToString("\n") { "  [${it.name}] ${it.error?.message ?: it.failedStep}" }
        throw AssertionError("${result.scenarios.count { !it.passed && !it.pending }} scenario(s) failed:\n$messages")
    }
}
