/**
 * ## Example 1: Basic Steps
 *
 * Demonstrates:
 * - Literal steps with no parameters
 * - `Background` steps (shared across all scenarios)
 * - All five Gherkin keywords: `Given`, `When`, `Then`, `And`, `But`
 *
 * Key points:
 * - `And`/`But` steps produce method names without keyword prefix, same as Given/When/Then.
 * - Background steps appear in the same interface as scenario steps.
 * - All methods are `suspend fun` — step execution is coroutine-based.
 * - Steps with identical normalised text across scenarios are deduplicated.
 */
package io.mcol.behave.examples.ex01_basic

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.DivergentStep

@BehaveFeature("features/01_basic_steps.feature")
class BasicSteps : BasicStepsSpec {

    private var appLaunched = false
    private var loggedIn = false
    private var onLoginPage = false

    override suspend fun theAppIsLaunched() {
        appLaunched = true
    }

    override suspend fun iAmOnTheLoginPage() {
        check(appLaunched)
        onLoginPage = true
    }

    override suspend fun iEnterValidCredentials() {
        check(onLoginPage)
    }

    override suspend fun iTapTheLoginButton() {
        loggedIn = true
        onLoginPage = false
    }

    @DivergentStep
    override suspend fun iSeeTheDashboard() {
        check(loggedIn)
    }

    override suspend fun iDoNotSeeTheLoginForm() {
        check(!onLoginPage)
    }
}
