/**
 * ## Example 19: Intentionally Divergent Steps via @DivergentStep
 *
 * `the user is logged in` appears in BOTH 19a_web_search.feature and
 * 19b_mobile_search.feature. The implementations are deliberately different (web seeds
 * a session cookie, mobile calls a debug-menu auto-login). Without `@DivergentStep`,
 * the processor errors with a guidance message; with the annotation on EVERY diverging
 * override, the divergence is accepted as intentional.
 *
 * If the two implementations were the same, the right answer would be a `@StepsMixin`.
 * The annotation is the escape hatch for the cases where shared text legitimately maps
 * to different platform-specific bodies.
 */
package io.mcol.behave.examples.ex19_divergent_steps

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.DivergentStep

@BehaveFeature("features/19a_web_search.feature")
class WebSearchSteps : WebSearchStepsSpec {

    private var sessionCookie: String? = null
    private var currentUrl: String = ""

    @DivergentStep
    override suspend fun theUserIsLoggedIn() {
        sessionCookie = "session_abc123"
    }

    override suspend fun iNavigateTo(string: String) {
        check(sessionCookie != null) { "Unauthenticated request" }
        currentUrl = string
    }

    override suspend fun theURLContains(string: String) {
        check(currentUrl.contains(string)) {
            "Expected URL to contain '$string' but was '$currentUrl'"
        }
    }
}
