/**
 * ## Example 18: Shared Steps via @StepsMixin
 *
 * `SessionMixin` exposes step bodies that mutate the **object under test**
 * ([AppSession]). Each implementing class provides its own `session` instance —
 * the mixin only declares it abstract.
 *
 * KSP detects this interface via `@StepsMixin` and:
 *  1. Records each default-bodied method's `(name, paramTypes)` signature.
 *  2. When generating a `*StepsSpec` whose feature file has a matching step,
 *     emits `interface XxxStepsSpec : SessionMixin {}` and omits the abstract
 *     declaration for that step.
 *
 * Result: `SettingsSteps` and `ProfileSteps` don't redeclare the shared steps
 * but each gets the SAME mutations applied to ITS OWN [AppSession] — the test
 * assertions verify state, not console output.
 */
package io.mcol.behave.examples.ex18_shared_mixin

import io.mcol.behave.annotations.StepsMixin

/**
 * The object under test for both features. Steps from [SessionMixin] mutate it,
 * feature-specific steps read it.
 */
class AppSession {
    var started: Boolean = false
    var currentUser: String? = null
    var currentScreen: String? = null
}

@StepsMixin
interface SessionMixin {
    /** Each implementing class provides its own session — mixin holds no state itself. */
    val session: AppSession

    suspend fun theAppIsStarted() {
        session.started = true
    }

    suspend fun iLogInAs(string: String) {
        check(session.started) { "Cannot log in — app was not started" }
        session.currentUser = string
    }
}
