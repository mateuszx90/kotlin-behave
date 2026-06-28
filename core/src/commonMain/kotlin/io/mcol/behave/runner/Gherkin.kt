package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.parser.GherkinParser
import io.mcol.behave.steps.StepDefinitions

private val featureCache = mutableMapOf<String, Feature>()

/**
 * Parse a .feature file. Path is classpath-relative, e.g. "features/foo.feature".
 *
 * Looks up [FeatureRegistry] first (populated at build time by code-gen); falls back to
 * [readResource] for runtime classpath/filesystem reads on platforms that support it.
 */
fun loadFeature(path: String): Feature = featureCache.getOrPut(path) {
    val content = FeatureRegistry.get(path) ?: readResource(path)
    GherkinParser.parse(content)
}

/** Run all scenarios in [path] against [steps]. Suspend — call from runTest {} or a Kotest spec. */
suspend fun <C> gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    retries: Int = 0,
) {
    val feature = loadFeature(path)
    val result = GherkinRunner(steps, tags, retries).run(feature)
    if (result.hasFailures) {
        val messages =
            result.scenarios
                .filter { !it.passed && !it.pending && !it.skipped }
                .joinToString("\n") { "  [${it.name}] ${it.error?.message ?: it.failedStep}" }
        throw AssertionError("${result.scenarios.count { !it.passed && !it.pending && !it.skipped }} scenario(s) failed:\n$messages")
    }
}

/**
 * Run each scenario through [runScenario] (e.g. a fresh Compose test per scenario).
 * [runScenario] receives a suspending [run] lambda — call it to execute the scenario's
 * Before hooks → Background steps → Scenario steps → After hooks.
 */
suspend fun <C> gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    retries: Int = 0,
    runScenario: suspend (ctx: C, run: suspend () -> Unit) -> Unit,
) {
    val feature = loadFeature(path)
    val result = GherkinRunner(steps, tags, retries).runWithPerScenarioRunner(feature, runScenario)
    if (result.hasFailures) {
        val messages =
            result.scenarios
                .filter { !it.passed && !it.pending && !it.skipped }
                .joinToString("\n") { "  [${it.name}] ${it.error?.message ?: it.failedStep}" }
        throw AssertionError("${result.scenarios.count { !it.passed && !it.pending && !it.skipped }} scenario(s) failed:\n$messages")
    }
}
