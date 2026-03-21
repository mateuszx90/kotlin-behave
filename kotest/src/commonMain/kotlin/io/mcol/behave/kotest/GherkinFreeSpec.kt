package io.mcol.behave.kotest

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.steps.MissingStepException
import io.mcol.behave.steps.PendingException
import io.mcol.behave.steps.StepDefinitions
import io.mcol.behave.runner.loadFeature

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
fun <C> FreeSpec.gherkin(path: String, steps: StepDefinitions<C>) {
    val feature = loadFeature(path)

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
