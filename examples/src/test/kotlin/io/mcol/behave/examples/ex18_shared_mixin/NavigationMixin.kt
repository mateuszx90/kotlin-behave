/**
 * Second mixin in this example. Demonstrates **multi-mixin composition**:
 * a generated `*StepsSpec` can extend several `@StepsMixin` interfaces at once,
 * and they can share the same abstract property (here: `session`) — satisfied
 * by a single override in the implementing class.
 *
 * KSP emits `interface SettingsStepsSpec : SessionMixin, NavigationMixin { ... }`
 * because both feature files contain steps that match methods declared here.
 */
package io.mcol.behave.examples.ex18_shared_mixin

import io.mcol.behave.annotations.StepsMixin

@StepsMixin
interface NavigationMixin {
    /** Shared with [SessionMixin]. A class that implements both mixins provides ONE override. */
    val session: AppSession

    suspend fun iAmOnTheHomeScreen() {
        check(session.started) { "Cannot navigate — app was not started" }
        session.currentScreen = "home"
    }

    suspend fun iGoBack() {
        check(session.currentScreen != null) { "Cannot go back — no current screen" }
        session.currentScreen = "home"
    }
}
