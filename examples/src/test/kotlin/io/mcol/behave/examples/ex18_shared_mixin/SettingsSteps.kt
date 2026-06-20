/**
 * Implements only the **feature-specific** steps of 18a_settings.feature.
 * The two shared steps come from [SessionMixin] via the generated `SettingsStepsSpec`.
 *
 * The generated interface looks like:
 * ```
 * interface SettingsStepsSpec : SessionMixin {
 *     // theAppIsStarted() — inherited from SessionMixin
 *     // iLogInAs(string) — inherited from SessionMixin
 *     suspend fun iChangeTheThemeTo(string: String)
 *     suspend fun theThemeIs(string: String)
 * }
 * ```
 *
 * The `session` field is required by the mixin and gates feature-specific steps
 * (changing the theme requires being logged in, which the mixin set up).
 */
package io.mcol.behave.examples.ex18_shared_mixin

import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/18a_settings.feature")
class SettingsSteps : SettingsStepsSpec {

    override val session = AppSession()

    private var theme: String = "light"

    override suspend fun iChangeTheThemeTo(string: String) {
        check(session.currentUser != null) { "Cannot change theme — no user logged in" }
        theme = string
    }

    override suspend fun theThemeIs(string: String) {
        check(theme == string) { "Expected theme '$string' but was '$theme'" }
    }
}
