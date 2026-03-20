package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
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
            runScenario(feature, scenario)
        }
        printResults(feature.name, results)
        return RunResult(feature.name, results)
    }

    private fun runScenario(feature: Feature, scenario: Scenario): ScenarioResult {
        // Swap ctx to a fresh instance — step lambdas read ctx via captured StepBuilder
        stepDefinitions.stepBuilder.ctx = stepDefinitions.factory()
        val allSteps = (feature.background?.steps ?: emptyList()) + scenario.steps
        for (step in allSteps) {
            val fn = stepDefinitions.find(step.text)
                ?: return ScenarioResult(
                    name = scenario.name,
                    passed = false,
                    error = MissingStepException(step.text),
                    failedStep = step.text,
                )
            try {
                fn()
            } catch (e: PendingException) {
                return ScenarioResult(scenario.name, passed = false, pending = true, failedStep = step.text)
            } catch (e: Throwable) {
                return ScenarioResult(scenario.name, passed = false, error = e, failedStep = step.text)
            }
        }
        return ScenarioResult(scenario.name, passed = true)
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
