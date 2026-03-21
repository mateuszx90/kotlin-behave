package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.parser.GherkinParser
import io.mcol.behave.steps.StepDefinitions

private val featureCache = mutableMapOf<String, Feature>()

/** Parse a .feature file from test resources. Path is classpath-relative, e.g. "features/foo.feature". */
fun loadFeature(path: String): Feature = featureCache.getOrPut(path) { GherkinParser.parse(readResource(path)) }

/** Run all scenarios in [path] against [steps]. Suspend — call from runTest {} or a Kotest spec. */
suspend fun <C> gherkin(path: String, steps: StepDefinitions<C>, tags: String? = null) {
    val feature = loadFeature(path)
    val result = GherkinRunner(steps, tags).run(feature)
    if (result.hasFailures) {
        val messages = result.scenarios
            .filter { !it.passed && !it.pending && !it.skipped }
            .joinToString("\n") { "  [${it.name}] ${it.error?.message ?: it.failedStep}" }
        throw AssertionError("${result.scenarios.count { !it.passed && !it.pending && !it.skipped }} scenario(s) failed:\n$messages")
    }
}

/**
 * Run each scenario through [runScenario] (e.g. a fresh Compose test per scenario).
 * [runScenario] receives a blocking [run] lambda — call it to execute the scenario's
 * Before hooks → Background steps → Scenario steps → After hooks.
 *
 * Non-suspend. The runner bridges to suspend executeScenario via SuspendBridge (JVM only).
 */
fun <C> gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    runScenario: (ctx: C, run: () -> Unit) -> Unit,
) {
    val feature = loadFeature(path)
    val result = GherkinRunner(steps, tags).runWithPerScenarioRunner(feature, runScenario)
    if (result.hasFailures) {
        val messages = result.scenarios
            .filter { !it.passed && !it.pending && !it.skipped }
            .joinToString("\n") { "  [${it.name}] ${it.error?.message ?: it.failedStep}" }
        throw AssertionError("${result.scenarios.count { !it.passed && !it.pending && !it.skipped }} scenario(s) failed:\n$messages")
    }
}
