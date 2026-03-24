/**
 * ## Example 12: Tags
 *
 * Demonstrates:
 * - `@tag` annotations on scenarios for runtime filtering
 * - Tag filter expressions: `@smoke`, `@smoke and not @slow`, `(@smoke or @critical) and not @wip`
 * - Tags do NOT affect code generation — all steps are generated regardless
 * - Without a `tags` parameter, all scenarios run
 */
package io.mcol.behave.examples.ex12_tags

import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/12_tags.feature")
class TagSteps : TagStepsSpec {

    private var loggedIn = false

    override suspend fun givenIAmLoggedIn() {
        loggedIn = true
    }

    override suspend fun thenISeeTheDashboard() {
        check(loggedIn)
    }

    override suspend fun andIHaveUnreadNotifications(int: Int) {
        check(int > 0)
    }

    override suspend fun thenISeeTheNotificationBadge() {
        check(loggedIn)
    }

    override suspend fun andTheDatabaseHasRecords(int: Int) {
        check(int > 0)
    }

    override suspend fun thenTheDashboardLoadsWithinSeconds(int: Int) {
        check(int > 0)
    }

    override suspend fun thenISeeTheAnalyticsWidget() {
        check(loggedIn)
    }
}

// KSP generates generatedTagSteps and TagGherkinTest (runs all scenarios).
// Below are additional test classes demonstrating tag filtering.
