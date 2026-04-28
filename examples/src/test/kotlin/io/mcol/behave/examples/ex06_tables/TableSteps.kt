/**
 * ## Example 6: Data Tables
 *
 * Demonstrates:
 * - Steps followed by a `| column |` table
 * - KSP auto-generates a Row data class with one `String` field per column header
 * - Row class name = method name (first letter uppercased) + `Row` suffix
 * - All columns default to `String` (use `@BehaveType` to override — see example 10)
 * - The trailing colon in `"the following users:"` is stripped from the method name
 */
package io.mcol.behave.examples.ex06_tables

import io.mcol.behave.annotations.BehaveFeature
import kotlin.test.assertEquals

@BehaveFeature("features/06_data_tables.feature")
class TableSteps : TableStepsSpec {

    private val users = mutableListOf<Map<String, String>>()

    // Auto-generated Row class: TheFollowingUsersRow(name, email, age) — all String
    override suspend fun theFollowingUsers(rows: List<TheFollowingUsersRow>) {
        rows.forEach { row ->
            users.add(mapOf("name" to row.name, "email" to row.email, "age" to row.age))
        }
    }

    override suspend fun usersAreRegistered(int: Int) {
        assertEquals(int, users.size)
    }
}
