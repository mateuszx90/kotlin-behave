/**
 * ## Example 1: Basic Steps
 *
 * Demonstrates:
 * - Literal steps with no parameters
 * - `Background` steps (shared across all scenarios)
 * - All five Gherkin keywords: `Given`, `When`, `Then`, `And`, `But`
 *
 * Key points:
 * - `And`/`But` keep their own prefix (`andXxx`, `butXxx`) — they are NOT
 *   resolved to the preceding Given/When/Then keyword.
 * - Background steps appear in the same interface as scenario steps.
 * - All methods are `suspend fun` — step execution is coroutine-based.
 * - Steps with identical normalised text across scenarios are deduplicated.
 */
package io.mcol.behave.examples.ex01_basic

import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/01_basic_steps.feature")
class BasicSteps : BasicStepsSpec {

    private var appLaunched = false
    private var loggedIn = false
    private var onLoginPage = false

    override suspend fun givenTheAppIsLaunched() {
        appLaunched = true
    }

    override suspend fun givenIAmOnTheLoginPage() {
        check(appLaunched)
        onLoginPage = true
    }

    override suspend fun whenIEnterValidCredentials() {
        check(onLoginPage)
    }

    override suspend fun andITapTheLoginButton() {
        loggedIn = true
        onLoginPage = false
    }

    override suspend fun thenISeeTheDashboard() {
        check(loggedIn)
    }

    override suspend fun butIDoNotSeeTheLoginForm() {
        check(!onLoginPage)
    }
}

