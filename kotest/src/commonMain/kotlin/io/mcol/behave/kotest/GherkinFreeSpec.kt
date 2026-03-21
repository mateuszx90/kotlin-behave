package io.mcol.behave.kotest

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.runner.GherkinRunner
import io.mcol.behave.runner.loadFeature
import io.mcol.behave.steps.Hook
import io.mcol.behave.steps.MissingStepException
import io.mcol.behave.steps.PendingException
import io.mcol.behave.steps.ScenarioInfo
import io.mcol.behave.steps.ScenarioStatus
import io.mcol.behave.steps.StepDefinitions

/**
 * Per-scenario variant for use with Compose UI tests (or any test that needs a
 * per-scenario setup/teardown wrapper).
 *
 * ```kotlin
 * class LearningGherkinTest : FreeSpec({
 *     gherkin("features/learning_screen.feature", learningSteps) { ctx, run ->
 *         runTestWithEnglishLocale { _ ->
 *             ctx.compose = this
 *             withScenarioTimeout(10_000) { run() }
 *         }
 *     }
 * })
 * ```
 *
 * Generated tree:
 *   Feature: <name>          ← container
 *     Scenario: <name>       ← leaf test (all steps run inside runScenario)
 *
 * [runScenario] receives the fresh ctx and a blocking [run] lambda. Call [run] to execute
 * Before hooks → Background steps → Scenario steps → After hooks for that scenario.
 * Non-suspend; bridges to suspend internally via SuspendBridge (JVM only).
 *
 * @param tags Optional tag filter expression, e.g. "@smoke and not @wip"
 */
fun <C> FreeSpec.gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    runScenario: (ctx: C, run: () -> Unit) -> Unit,
) {
    val feature = loadFeature(path)
    "Feature: ${feature.name}" - {
        for (scenario in feature.scenarios) {
            "Scenario: ${scenario.name}" {
                val singleScenario = feature.copy(scenarios = listOf(scenario))
                val result = GherkinRunner(steps, tags).runWithPerScenarioRunner(singleScenario, runScenario)
                if (result.hasFailures) {
                    val failed = result.scenarios.first { !it.passed && !it.pending && !it.skipped }
                    throw failed.error ?: AssertionError("Scenario '${failed.name}' failed at step: ${failed.failedStep}")
                }
            }
        }
    }
}

/**
 * Run a Gherkin feature file as a Kotest FreeSpec test tree.
 *
 * ```kotlin
 * class MyTest : FreeSpec({
 *     gherkin("features/my_feature.feature", mySteps)
 * })
 * ```
 *
 * Generated tree:
 *   Feature: <name>           ← container
 *     Scenario: <name>        ← leaf test (all steps + hooks run together)
 *
 * Background steps execute before each scenario's own steps.
 * Before/After hooks run as part of each scenario leaf test.
 * ctx is reset per scenario.
 *
 * Pending steps: the scenario test is marked as ignored with "[PENDING]" printed.
 * Steps after failure: the scenario throws immediately.
 *
 * @param tags Optional tag filter expression, e.g. "@smoke and not @wip"
 * @param scenarioAsTest When `true` (default) each scenario is a single leaf test. When `false`
 *   each step is its own leaf test inside a scenario container (classic BDD IDE reporting,
 *   hooks run via beforeContainer/afterContainer).
 */
fun <C> FreeSpec.gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    scenarioAsTest: Boolean = true,
) {
    val feature = loadFeature(path)

    if (scenarioAsTest) {
        "Feature: ${feature.name}" - {
            for (scenario in feature.scenarios) {
                "Scenario: ${scenario.name}" {
                    val singleScenario = feature.copy(scenarios = listOf(scenario))
                    val result = GherkinRunner(steps, tags).run(singleScenario)
                    if (result.hasFailures) {
                        val failed = result.scenarios.first { !it.passed && !it.pending && !it.skipped }
                        throw failed.error ?: AssertionError("Scenario '${failed.name}' failed at step: ${failed.failedStep}")
                    }
                    if (result.scenarios.any { it.pending }) {
                        println("  [PENDING] ${scenario.name}")
                    }
                }
            }
        }
    } else {
        // Step-level tree: each step is a leaf test.
        // Hooks are dispatched in beforeContainer/afterContainer at the Scenario: container level.
        beforeContainer { testCase ->
            if (testCase.name.testName.startsWith("Scenario:")) {
                val scenarioName = testCase.name.testName.removePrefix("Scenario: ")
                val scenarioTags = feature.scenarios.firstOrNull { it.name == scenarioName }?.tags ?: emptySet()
                val info = ScenarioInfo(scenarioName, scenarioTags, ScenarioStatus.Passed)
                steps.stepBuilder.ctx = steps.factory()
                steps.stepBuilder.beforeHooks.forEach { hook -> dispatchHook(hook, info, steps.stepBuilder.ctx) }
            }
        }
        afterContainer { (testCase, result) ->
            if (testCase.name.testName.startsWith("Scenario:")) {
                val scenarioName = testCase.name.testName.removePrefix("Scenario: ")
                val scenarioTags = feature.scenarios.firstOrNull { it.name == scenarioName }?.tags ?: emptySet()
                val status = if (result.isSuccess) ScenarioStatus.Passed else ScenarioStatus.Failed
                val info = ScenarioInfo(scenarioName, scenarioTags, status)
                steps.stepBuilder.afterHooks.asReversed().forEach { hook ->
                    runCatching { dispatchHook(hook, info, steps.stepBuilder.ctx) }
                }
            }
        }

        "Feature: ${feature.name}" - {
            for (scenario in feature.scenarios) {
                val scenarioSteps = (feature.background?.steps ?: emptyList()) + scenario.steps
                "Scenario: ${scenario.name}" - {
                    var scenarioFailed = false
                    for (step in scenarioSteps) {
                        val stepFn = steps.find(step)
                        step.text {
                            if (!scenarioFailed) {
                                try {
                                    stepFn?.invoke() ?: throw MissingStepException(step.text)
                                } catch (e: PendingException) {
                                    scenarioFailed = true
                                    println("  [PENDING] ${step.text}")
                                } catch (e: Throwable) {
                                    scenarioFailed = true
                                    throw e
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun <C> dispatchHook(hook: Hook<C>, info: ScenarioInfo, ctx: C) {
    when (hook) {
        is Hook.WithCtx            -> hook.block(ctx)
        is Hook.WithScenarioAndCtx -> hook.block(info, ctx)
    }
}
