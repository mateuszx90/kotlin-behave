package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Scenario
import io.mcol.behave.steps.MissingStepException
import io.mcol.behave.steps.PendingException
import io.mcol.behave.steps.StepDefinitions

data class ScenarioResult(
    val name: String,
    val passed: Boolean,
    val pending: Boolean = false,
    val error: Throwable? = null,
    val failedStep: String? = null,
)

data class RunResult(
    val featureName: String,
    val scenarios: List<ScenarioResult>,
) {
    val hasFailures: Boolean get() = scenarios.any { !it.passed && !it.pending }
}

class GherkinRunner<C>(private val stepDefinitions: StepDefinitions<C>) {

    fun run(feature: Feature): RunResult {
        val results = feature.scenarios.map { scenario ->
            stepDefinitions.stepBuilder.ctx = stepDefinitions.factory()
            executeScenario(feature, scenario)
        }
        printResults(feature.name, results)
        return RunResult(feature.name, results)
    }

    fun runWithPerScenarioRunner(
        feature: Feature,
        runScenario: (ctx: C, run: () -> Unit) -> Unit,
    ): RunResult {
        val results = feature.scenarios.map { scenario ->
            val ctx = stepDefinitions.factory()
            stepDefinitions.stepBuilder.ctx = ctx
            var result = ScenarioResult(scenario.name, passed = true)
            val run = { result = executeScenario(feature, scenario) }
            runScenario(ctx, run)
            result
        }
        printResults(feature.name, results)
        return RunResult(feature.name, results)
    }

    private fun executeScenario(feature: Feature, scenario: Scenario): ScenarioResult {
        val beforeHooks = stepDefinitions.stepBuilder.beforeHooks
        val afterHooks  = stepDefinitions.stepBuilder.afterHooks

        var stepError: Throwable? = null
        var failedStep: String?   = null
        var isPending             = false

        // Before hooks — stop on first failure
        for (hook in beforeHooks) {
            if (stepError != null) break
            try { hook() } catch (e: Throwable) { stepError = e; failedStep = "<Before hook>" }
        }

        // Steps — only if Before passed; stop on first failure
        if (stepError == null) {
            val allSteps = (feature.background?.steps ?: emptyList()) + scenario.steps
            for (step in allSteps) {
                if (stepError != null || isPending) break
                val fn = stepDefinitions.find(step.text) ?: run {
                    stepError = MissingStepException(step.text)
                    failedStep = step.text
                    null
                }
                fn?.let {
                    try { it() }
                    catch (e: PendingException) { isPending = true; failedStep = step.text }
                    catch (e: Throwable)        { stepError = e; failedStep = step.text }
                }
            }
        }

        // After hooks — always run in reverse; record first error only
        for (hook in afterHooks.asReversed()) {
            try { hook() }
            catch (e: Throwable) {
                if (stepError == null) { stepError = e; failedStep = "<After hook>" }
            }
        }

        return when {
            isPending         -> ScenarioResult(scenario.name, passed = false, pending = true, failedStep = failedStep)
            stepError != null -> ScenarioResult(scenario.name, passed = false, error = stepError, failedStep = failedStep)
            else              -> ScenarioResult(scenario.name, passed = true)
        }
    }

    private fun printResults(featureName: String, results: List<ScenarioResult>) {
        println("\nFeature: $featureName\n")
        for (r in results) {
            when {
                r.passed  -> println("  ✓ Scenario: ${r.name}")
                r.pending -> println("  ~ Scenario: ${r.name}\n      Step: ${r.failedStep}  [PENDING]")
                else      -> println("  ✗ Scenario: ${r.name}\n      Step: ${r.failedStep}\n      ${r.error?.message}")
            }
        }
        val passed  = results.count { it.passed }
        val failed  = results.count { !it.passed && !it.pending }
        val pending = results.count { it.pending }
        println("\n$passed passed, $failed failed, $pending pending\n")
    }
}
