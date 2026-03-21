package io.mcol.behave.kotest

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.runner.GherkinRunner
import io.mcol.behave.runner.loadFeature
import io.mcol.behave.steps.MissingStepException
import io.mcol.behave.steps.PendingException
import io.mcol.behave.steps.StepDefinitions

/**
 * Register a Gherkin feature file as a Kotest FreeSpec test tree.
 *
 * ```kotlin
 * class MyTest : FreeSpec({
 *     gherkin("features/my_feature.feature", mySteps)
 * })
 * ```
 *
 * Generated tree:
 *   Feature: <name>           ← container
 *     Scenario: <name>        ← container
 *       Given <step text>     ← leaf test
 *       When  <step text>     ← leaf test
 *       Then  <step text>     ← leaf test
 *
 * Background steps appear as the first leaf nodes within each scenario.
 * Before/After hooks use Kotest's beforeContainer/afterContainer, filtered to
 * containers whose name starts with "Scenario:". ctx is reset per scenario.
 *
 * Known limitation: scenarioFailed is initialised once at collection time and
 * not reset between re-runs of the same spec instance.
 *
 * Pending steps: appear green with "[PENDING]" in console; remaining steps skip.
 * Steps after failure: silently skip (appear green/no-op in the IDE tree).
 */
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
 */
fun <C> FreeSpec.gherkin(
    path: String,
    steps: StepDefinitions<C>,
    runScenario: (ctx: C, run: () -> Unit) -> Unit,
) {
    val feature = loadFeature(path)
    "Feature: ${feature.name}" - {
        for (scenario in feature.scenarios) {
            "Scenario: ${scenario.name}" {
                val singleScenario = feature.copy(scenarios = listOf(scenario))
                val result = GherkinRunner(steps).runWithPerScenarioRunner(singleScenario, runScenario)
                if (result.hasFailures) {
                    val failed = result.scenarios.first { !it.passed && !it.pending }
                    throw failed.error ?: AssertionError("Scenario '${failed.name}' failed at step: ${failed.failedStep}")
                }
            }
        }
    }
}

/**
 * @param scenarioAsTest when `true` each scenario is a single leaf test that runs all steps
 *   together (hooks included). When `false` (default) each step is its own leaf test inside
 *   a scenario container, matching classic BDD IDE reporting.
 */
fun <C> FreeSpec.gherkin(
    path: String,
    steps: StepDefinitions<C>,
    scenarioAsTest: Boolean = false,
) {
    val feature = loadFeature(path)

    if (scenarioAsTest) {
        "Feature: ${feature.name}" - {
            for (scenario in feature.scenarios) {
                val scenarioSteps = (feature.background?.steps ?: emptyList()) + scenario.steps
                "Scenario: ${scenario.name}" {
                    steps.stepBuilder.ctx = steps.factory()
                    steps.stepBuilder.beforeHooks.forEach { it() }
                    try {
                        for (step in scenarioSteps) {
                            val stepFn = steps.find(step.text)
                            stepFn?.invoke() ?: throw MissingStepException(step.text)
                        }
                    } finally {
                        steps.stepBuilder.afterHooks.asReversed().forEach { runCatching { it() } }
                    }
                }
            }
        }
    } else {
        beforeContainer { testCase ->
            if (testCase.name.testName.startsWith("Scenario:")) {
                steps.stepBuilder.ctx = steps.factory()
                steps.stepBuilder.beforeHooks.forEach { it() }
            }
        }
        afterContainer { (testCase, _) ->
            if (testCase.name.testName.startsWith("Scenario:")) {
                steps.stepBuilder.afterHooks.asReversed().forEach { runCatching { it() } }
            }
        }

        "Feature: ${feature.name}" - {
            for (scenario in feature.scenarios) {
                val scenarioSteps = (feature.background?.steps ?: emptyList()) + scenario.steps
                "Scenario: ${scenario.name}" - {
                    var scenarioFailed = false
                    for (step in scenarioSteps) {
                        val stepFn = steps.find(step.text)
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
