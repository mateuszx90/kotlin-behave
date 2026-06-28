/**
 * ## Example 29: Vertical key-value tables and `DataTable.diff`
 *
 * Demonstrates:
 * - `DataTable.toObject { map -> ... }` maps a vertical (transposed) key→value table to a
 *   domain object — each grid row is a `field | value` pair.
 * - `DataTable.diff(actual)` asserts an actual table matches the expected one, throwing a
 *   `+`/`-` report on mismatch (Cucumber's `DataTable.diff`).
 *
 * Wired manually with the core `steps { }` DSL (no `@BehaveFeature`) so the step bodies can
 * reach `params.dataTable` directly instead of a generated row class.
 */
package io.mcol.behave.examples.ex29_keyvalue_tables

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin
import io.mcol.behave.model.DataTable
import io.mcol.behave.steps.steps

data class User(val name: String, val email: String, val age: Int)

class RosterCtx {
    var user: User? = null
    var expected: DataTable? = null
}

val keyValueTableSteps = steps(::RosterCtx) {

    Given("a user") { params ->
        ctx.user = params.dataTable!!.toObject { m ->
            User(name = m["name"] ?: "", email = m["email"] ?: "", age = (m["age"] ?: "0").toInt())
        }
    }

    Then("the user is named {string} aged {int}") { params ->
        val name = params[0] as String
        val age = params[1] as Int
        check(ctx.user == User(name, "alice@example.com", age)) { "unexpected user: ${ctx.user}" }
    }

    Given("the expected roster") { params ->
        ctx.expected = params.dataTable
    }

    Then("the actual roster matches the expected table") {
        val actual = DataTable(
            listOf(
                mapOf("name" to "Alice", "age" to "30"),
                mapOf("name" to "Bob", "age" to "25"),
            ),
        )
        // Throws TableDiffException on mismatch; the scenario passes when the tables are equal.
        ctx.expected!!.diff(actual)
    }
}

/** Runs the key-value tables feature with the manually wired steps. */
class KeyValueTableGherkinTest :
    FreeSpec({
        gherkin("features/29_keyvalue_tables.feature", keyValueTableSteps)
    })
