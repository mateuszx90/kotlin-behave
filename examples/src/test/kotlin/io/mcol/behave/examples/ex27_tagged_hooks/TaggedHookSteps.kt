/**
 * ## Example 27: Tag-scoped hooks — `Before("@tag")` / `After("@tag")`
 *
 * Demonstrates:
 * - `Before("@db") { ... }` runs only for scenarios whose tags satisfy the expression.
 * - Untagged hooks (plain `Before { }`) keep running for every scenario.
 * - The tag expression supports the same `and` / `or` / `not` grammar as the tag filter.
 *
 * Here the `@db` scenario sees `dbReady == true` (the scoped hook fired), while the plain
 * scenario sees it `false` (the hook was skipped). ctx is fresh per scenario.
 */
package io.mcol.behave.examples.ex27_tagged_hooks

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.steps.steps

@BehaveFeature("features/27_tagged_hooks.feature", generateTest = false)
class TaggedHookSteps : TaggedHookStepsSpec {

    var dbReady = false

    override suspend fun aCleanSlate() {
        // no-op: the per-scenario ctx is already clean
    }

    override suspend fun theDatabaseWasPrepared() {
        check(dbReady) { "expected the @db hook to have prepared the database" }
    }

    override suspend fun theDatabaseWasNotPrepared() {
        check(!dbReady) { "expected the @db hook to have been skipped for this scenario" }
    }
}

// Manually wired steps with a tag-scoped Before hook.
val taggedHookSteps = steps({ TaggedHookSteps() }) {

    // Only fires for scenarios tagged @db.
    Before("@db") { ctx -> ctx.dbReady = true }

    Given("a clean slate") { ctx.aCleanSlate() }
    Then("the database was prepared") { ctx.theDatabaseWasPrepared() }
    Then("the database was not prepared") { ctx.theDatabaseWasNotPrepared() }
}
