package io.mcol.behave.runner

import io.mcol.behave.model.Feature
import io.mcol.behave.model.Scenario
import io.mcol.behave.steps.Hook
import io.mcol.behave.steps.MissingStepException
import io.mcol.behave.steps.PendingException
import io.mcol.behave.steps.ScenarioInfo
import io.mcol.behave.steps.ScenarioStatus
import io.mcol.behave.steps.StepDefinitions
import io.mcol.behave.steps.StepHook
import io.mcol.behave.steps.StepInfo

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
    private val retries: Int = 0,
) {

    private val tagFilter: TagFilter? by lazy {
        val expr = tags ?: getSystemProperty("behave.tags")
        expr?.takeIf { it.isNotBlank() }?.let { parseTagFilter(it) }
    }

    /**
     * Number of *extra* attempts a scenario gets. Honors the [retries] passed in, and bumps it to
     * at least [FLAKY_DEFAULT_RETRIES] for scenarios tagged `@flaky` / `@retry`.
     */
    private fun retryBudget(scenarioTags: Set<String>): Int {
        val isFlaky = scenarioTags.any { it == "@flaky" || it == "@retry" }
        return if (isFlaky) maxOf(retries, FLAKY_DEFAULT_RETRIES) else retries
    }

    private fun ScenarioResult.isRetryable(): Boolean = !passed && !pending && !skipped

    suspend fun run(feature: Feature): RunResult {
        val results = feature.scenarios.map { scenario ->
            if (tagFilter != null && !tagFilter!!.matches(scenario.tags)) {
                stepDefinitions.stepBuilder.ctx = stepDefinitions.factory()
                val info = ScenarioInfo(scenario.name, scenario.tags, ScenarioStatus.Skipped)
                runAfterHooksForSkipped(info)
                ScenarioResult(scenario.name, passed = false, skipped = true)
            } else {
                val budget = retryBudget(scenario.tags)
                var result: ScenarioResult
                var attempt = 0
                do {
                    stepDefinitions.stepBuilder.ctx = stepDefinitions.factory()
                    result = executeScenario(feature, scenario)
                    attempt++
                } while (result.isRetryable() && attempt <= budget)
                result
            }
        }
        printResults(feature.name, results)
        return RunResult(feature.name, results)
    }

    suspend fun runWithPerScenarioRunner(
        feature: Feature,
        runScenario: suspend (ctx: C, run: suspend () -> Unit) -> Unit,
    ): RunResult {
        val results = feature.scenarios.map { scenario ->
            if (tagFilter != null && !tagFilter!!.matches(scenario.tags)) {
                // Do not run after hooks: runScenario was never called, so the test environment
                // (e.g. Compose UI, database) was never set up. After hooks would run against
                // an uninitialised ctx.
                ScenarioResult(scenario.name, passed = false, skipped = true)
            } else {
                val budget = retryBudget(scenario.tags)
                var result: ScenarioResult
                var attempt = 0
                do {
                    val ctx = stepDefinitions.factory()
                    stepDefinitions.stepBuilder.ctx = ctx
                    result = ScenarioResult(scenario.name, passed = true)
                    val run: suspend () -> Unit = { result = executeScenario(feature, scenario) }
                    runScenario(ctx, run)
                    attempt++
                } while (result.isRetryable() && attempt <= budget)
                result
            }
        }
        printResults(feature.name, results)
        return RunResult(feature.name, results)
    }

    private suspend fun runAfterHooksForSkipped(info: ScenarioInfo) {
        for (hook in stepDefinitions.stepBuilder.afterHooks.asReversed()) {
            try {
                dispatchHook(hook, info, stepDefinitions.stepBuilder.ctx)
            } catch (_: Throwable) { }
        }
    }

    internal suspend fun executeScenario(feature: Feature, scenario: Scenario): ScenarioResult {
        val beforeHooks = stepDefinitions.stepBuilder.beforeHooks
        val afterHooks = stepDefinitions.stepBuilder.afterHooks
        val scenarioInfoBefore = ScenarioInfo(scenario.name, scenario.tags, ScenarioStatus.Passed)

        var stepError: Throwable? = null
        var failedStep: String? = null
        var isPending = false

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
                    val stepInfo = StepInfo(step.keyword.name, step.text)
                    val ctx = stepDefinitions.stepBuilder.ctx
                    try {
                        runBeforeStepHooks(stepInfo, ctx)
                        it()
                    } catch (e: PendingException) {
                        isPending = true
                        failedStep = step.text
                    } catch (e: Throwable) {
                        stepError = e
                        failedStep = step.text
                    } finally {
                        // AfterStep always runs (e.g. screenshot on failure); record its own failure
                        // only if the step itself passed.
                        val afterErr = runAfterStepHooks(stepInfo, ctx)
                        if (afterErr != null && stepError == null && !isPending) {
                            stepError = afterErr
                            failedStep = step.text
                        }
                    }
                }
            }
        }

        val finalStatus = when {
            isPending -> ScenarioStatus.Pending
            stepError != null -> ScenarioStatus.Failed
            else -> ScenarioStatus.Passed
        }
        val scenarioInfoAfter = ScenarioInfo(scenario.name, scenario.tags, finalStatus)

        for (hook in afterHooks.asReversed()) {
            try {
                dispatchHook(hook, scenarioInfoAfter, stepDefinitions.stepBuilder.ctx)
            } catch (e: Throwable) {
                if (stepError == null) {
                    stepError = e
                    failedStep = "<After hook>"
                }
            }
        }

        return when {
            isPending -> ScenarioResult(scenario.name, passed = false, pending = true, failedStep = failedStep)
            stepError != null -> ScenarioResult(scenario.name, passed = false, error = stepError, failedStep = failedStep)
            else -> ScenarioResult(scenario.name, passed = true)
        }
    }

    private suspend fun <C> dispatchHook(hook: Hook<C>, info: ScenarioInfo, ctx: C) {
        when (hook) {
            is Hook.WithCtx -> hook.block(ctx)
            is Hook.WithScenarioAndCtx -> hook.block(info, ctx)
        }
    }

    private suspend fun <C> dispatchStepHook(hook: StepHook<C>, info: StepInfo, ctx: C) {
        when (hook) {
            is StepHook.WithCtx -> hook.block(ctx)
            is StepHook.WithStepAndCtx -> hook.block(info, ctx)
        }
    }

    private suspend fun runBeforeStepHooks(info: StepInfo, ctx: C) {
        for (hook in stepDefinitions.stepBuilder.beforeStepHooks) dispatchStepHook(hook, info, ctx)
    }

    /** Runs every AfterStep hook (reverse order); returns the first failure, if any. */
    private suspend fun runAfterStepHooks(info: StepInfo, ctx: C): Throwable? {
        var err: Throwable? = null
        for (hook in stepDefinitions.stepBuilder.afterStepHooks.asReversed()) {
            try {
                dispatchStepHook(hook, info, ctx)
            } catch (e: Throwable) {
                if (err == null) err = e
            }
        }
        return err
    }

    internal fun printResults(featureName: String, results: List<ScenarioResult>) {
        println("\nFeature: $featureName\n")
        for (r in results) {
            when {
                r.skipped -> println("  - Scenario: ${r.name}  [SKIPPED]")
                r.passed -> println("  ✓ Scenario: ${r.name}")
                r.pending -> println("  ~ Scenario: ${r.name}\n      Step: ${r.failedStep}  [PENDING]")
                else -> println("  ✗ Scenario: ${r.name}\n      Step: ${r.failedStep}\n      ${r.error?.message}")
            }
        }
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed && !it.pending && !it.skipped }
        val pending = results.count { it.pending }
        val skipped = results.count { it.skipped }
        println("\n$passed passed, $failed failed, $pending pending, $skipped skipped\n")
    }

    companion object {
        /** Extra attempts granted to a scenario tagged `@flaky` / `@retry` when no count is set. */
        const val FLAKY_DEFAULT_RETRIES: Int = 1
    }
}
