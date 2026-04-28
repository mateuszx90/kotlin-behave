/**
 * ## Example 13: Hooks — Before/After
 *
 * Demonstrates:
 * - `Before { ctx -> ... }` — runs before each scenario
 * - `After { ctx -> ... }` — runs after each scenario (REVERSE order)
 * - `Before { info: ScenarioInfo, ctx -> ... }` — with scenario metadata
 * - `After { info: ScenarioInfo, ctx -> ... }` — with scenario status
 *
 * Execution order per scenario:
 * 1. Before hooks (registration order)
 * 2. Background steps
 * 3. Scenario steps
 * 4. After hooks (REVERSE registration order)
 *
 * Note: The generated companion (from KSP) does NOT include hooks.
 * Add them manually via the `steps()` builder when you need setup/teardown.
 */
package io.mcol.behave.examples.ex13_hooks

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.steps.ScenarioInfo
import io.mcol.behave.steps.steps

@BehaveFeature("features/13_hooks.feature", generateTest = false)
class HookSteps : HookStepsSpec {

    val db = mutableSetOf<String>()
    val hookLog = mutableListOf<String>()

    override suspend fun iInsertAUser(string: String) {
        db.add(string)
    }

    override suspend fun theUserExistsInTheDatabase(string: String) {
        check(string in db) { "User '$string' not found" }
    }

    override suspend fun iDeleteTheUser(string: String) {
        db.remove(string)
    }

    override suspend fun theUserDoesNotExistInTheDatabase(string: String) {
        check(string !in db) { "User '$string' should not exist" }
    }
}

// Manually wired steps with hooks
val hookSteps = steps({ HookSteps() }) {

    // Before: context only — runs before each scenario
    Before { ctx ->
        ctx.db.clear()
        ctx.hookLog.add("before-ctx")
    }

    // Before: with ScenarioInfo — access scenario name and tags
    Before { info: ScenarioInfo, ctx ->
        ctx.hookLog.add("before-info:${info.name}")
    }

    // After: context only
    After { ctx ->
        ctx.hookLog.add("after-ctx")
    }

    // After: with ScenarioInfo — includes scenario status (Passed/Failed)
    After { info: ScenarioInfo, ctx ->
        ctx.hookLog.add("after-info:${info.name}:${info.status}")
    }

    // Step registrations
    When("I insert a user {string}") { (s: String) -> ctx.iInsertAUser(s) }
    Then("the user {string} exists in the database") { (s: String) -> ctx.theUserExistsInTheDatabase(s) }
    And("I delete the user {string}") { (s: String) -> ctx.iDeleteTheUser(s) }
    Then("the user {string} does not exist in the database") { (s: String) -> ctx.theUserDoesNotExistInTheDatabase(s) }
}
