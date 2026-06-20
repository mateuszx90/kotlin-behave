/**
 * Implements only the **feature-specific** steps of 18b_profile.feature.
 * Three shared steps come from the mixins via the generated spec:
 *
 * ```
 * interface ProfileStepsSpec : SessionMixin, NavigationMixin {
 *     // theAppIsStarted(), iLogInAs(string) — SessionMixin
 *     // iAmOnTheHomeScreen(), iGoBack() — NavigationMixin
 *     suspend fun iOpenMyProfile()
 *     suspend fun iSeeTheUserName(string: String)
 *     suspend fun iSeeTheCurrentScreen(string: String)
 * }
 * ```
 *
 * `iOpenMyProfile` mutates `session.currentScreen` so the mixin's `iGoBack` has
 * something to navigate back FROM — illustrates feature-specific code and shared
 * code mutating the same object under test.
 */
package io.mcol.behave.examples.ex18_shared_mixin

import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/18b_profile.feature")
class ProfileSteps : ProfileStepsSpec {

    override val session = AppSession()

    private var profileOpenFor: String? = null

    override suspend fun iOpenMyProfile() {
        profileOpenFor = checkNotNull(session.currentUser) { "Cannot open profile — no user logged in" }
        session.currentScreen = "profile"
    }

    override suspend fun iSeeTheUserName(string: String) {
        check(profileOpenFor == string) { "Expected profile for '$string' but was '$profileOpenFor'" }
    }

    override suspend fun iSeeTheCurrentScreen(string: String) {
        check(session.currentScreen == string) {
            "Expected current screen '$string' but was '${session.currentScreen}'"
        }
    }
}
