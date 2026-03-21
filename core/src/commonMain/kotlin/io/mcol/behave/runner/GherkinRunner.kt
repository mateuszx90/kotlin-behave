package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Scenario
import io.mcol.behave.steps.Hook
import io.mcol.behave.steps.MissingStepException
import io.mcol.behave.steps.PendingException
import io.mcol.behave.steps.ScenarioInfo
import io.mcol.behave.steps.ScenarioStatus
import io.mcol.behave.steps.StepDefinitions

data class ScenarioResult(
    val name: String,
    val passed: Boolean,
    val pending: Boolean = false,
    val skipped: Boolean = false,
    val error: Throwable? = null,
    val failedStep: String? = null,
)

data class RunResult(
    val featureName: String,
    val scenarios: List<ScenarioResult>,
) {
    val hasFailures: Boolean get() = scenarios.any { !it.passed && !it.pending && !it.skipped }
}

class GherkinRunner<C>(
    private val stepDefinitions: StepDefinitions<C>,
    private val tags: String? = null,
) {

    private val tagFilter: TagFilter? by lazy {
        val expr = tags ?: getSystemProperty("behave.tags")
        expr?.takeIf { it.isNotBlank() }?.let { parseTagFilter(it) }
    }

    suspend fun run(feature: Feature): RunResult {
        val results = feature.scenarios.map { scenario ->
            if (tagFilter != null && !tagFilter!!.matches(scenario.tags)) {
                val info = ScenarioInfo(scenario.name, scenario.tags, ScenarioStatus.Skipped)
                runAfterHooksForSkipped(info)
                ScenarioResult(scenario.name, passed = false, skipped = true)
            } else {
                stepDefinitions.stepBuilder.ctx = stepDefinitions.factory()
                executeScenario(feature, scenario)
            }
        }
        printResults(feature.name, results)
        return RunResult(feature.name, results)
    }

    /**
     * Non-suspend: runScenario and the run lambda it receives are plain blocking.
     * executeScenario (suspend) is bridged via runSuspendBlocking — JVM only.
     */
    fun runWithPerScenarioRunner(
        feature: Feature,
        runScenario: (ctx: C, run: () -> Unit) -> Unit,
    ): RunResult {
        val results = feature.scenarios.map { scenario ->
            if (tagFilter != null && !tagFilter!!.matches(scenario.tags)) {
                val info = ScenarioInfo(scenario.name, scenario.tags, ScenarioStatus.Skipped)
                runSuspendBlocking { runAfterHooksForSkipped(info) }
                ScenarioResult(scenario.name, passed = false, skipped = true)
            } else {
                val ctx = stepDefinitions.factory()
                stepDefinitions.stepBuilder.ctx = ctx
                var result = ScenarioResult(scenario.name, passed = true)
                val run: () -> Unit = { result = runSuspendBlocking { executeScenario(feature, scenario) } }
                runScenario(ctx, run)
                result
            }
        }
        printResults(feature.name, results)
        return RunResult(feature.name, results)
    }

    private suspend fun runAfterHooksForSkipped(info: ScenarioInfo) {
        for (hook in stepDefinitions.stepBuilder.afterHooks.asReversed()) {
            try { dispatchHook(hook, info, stepDefinitions.stepBuilder.ctx) } catch (_: Throwable) { }
        }
    }

    internal suspend fun executeScenario(feature: Feature, scenario: Scenario): ScenarioResult {
        val beforeHooks = stepDefinitions.stepBuilder.beforeHooks
        val afterHooks  = stepDefinitions.stepBuilder.afterHooks
        val scenarioInfoBefore = ScenarioInfo(scenario.name, scenario.tags, ScenarioStatus.Passed)

        var stepError: Throwable? = null
        var failedStep: String?   = null
        var isPending             = false

        for (hook in beforeHooks) {
            if (stepError != null) break
            try {
                dispatchHook(hook, scenarioInfoBefore, stepDefinitions.stepBuilder.ctx)
            } catch (e: Throwable) {
                stepError = e
                failedStep = "<Before hook>"
            }
        }

        if (stepError == null) {
            val allSteps = (feature.background?.steps ?: emptyList()) + scenario.steps
            for (step in allSteps) {
                if (stepError != null || isPending) break
                val fn = stepDefinitions.find(step) ?: run {
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

        val finalStatus = when {
            isPending         -> ScenarioStatus.Pending
            stepError != null -> ScenarioStatus.Failed
            else              -> ScenarioStatus.Passed
        }
        val scenarioInfoAfter = ScenarioInfo(scenario.name, scenario.tags, finalStatus)

        for (hook in afterHooks.asReversed()) {
            try { dispatchHook(hook, scenarioInfoAfter, stepDefinitions.stepBuilder.ctx) }
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

    private suspend fun <C> dispatchHook(hook: Hook<C>, info: ScenarioInfo, ctx: C) {
        when (hook) {
            is Hook.WithCtx            -> hook.block(ctx)
            is Hook.WithScenarioAndCtx -> hook.block(info, ctx)
        }
    }

    internal fun printResults(featureName: String, results: List<ScenarioResult>) {
        println("\nFeature: $featureName\n")
        for (r in results) {
            when {
                r.skipped -> println("  - Scenario: ${r.name}  [SKIPPED]")
                r.passed  -> println("  ✓ Scenario: ${r.name}")
                r.pending -> println("  ~ Scenario: ${r.name}\n      Step: ${r.failedStep}  [PENDING]")
                else      -> println("  ✗ Scenario: ${r.name}\n      Step: ${r.failedStep}\n      ${r.error?.message}")
            }
        }
        val passed  = results.count { it.passed }
        val failed  = results.count { !it.passed && !it.pending && !it.skipped }
        val pending = results.count { it.pending }
        val skipped = results.count { it.skipped }
        println("\n$passed passed, $failed failed, $pending pending, $skipped skipped\n")
    }
}
