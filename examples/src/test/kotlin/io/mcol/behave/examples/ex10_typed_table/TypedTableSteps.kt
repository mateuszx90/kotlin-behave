/**
 * ## Example 10: DataTable with @BehaveType
 *
 * Demonstrates:
 * - `@BehaveType(type = Pet::class)` auto-detects columns matching constructor params
 *   (`name`, `breed`) and groups them into a composite field in the Row class
 * - `@BehaveType(placeholder = "age")` maps a single column to a custom type
 * - Unmapped columns stay as `String` in the Row class
 * - If a single `@BehaveType` covers ALL columns, no Row class is generated —
 *   the method takes `List<YourType>` directly
 */
package io.mcol.behave.examples.ex10_typed_table

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.BehaveType
import kotlin.test.assertEquals

data class Pet(val name: String, val breed: String)
data class PetAge(val years: Int)

@BehaveFeature("features/10_datatable_typed.feature")
@BehaveType(type = Pet::class)
@BehaveType(placeholder = "age", type = PetAge::class)
class TypedTableSteps : TypedTableStepsSpec {

    private var petCount = 0

    override suspend fun theFollowingPets(rows: List<TheFollowingPetsRow>) {
        petCount = rows.size
        // Row has: name, breed, age — all String
        rows.forEach { row ->
            check(row.name.isNotEmpty())
            check(row.breed.isNotEmpty())
            check(row.age.isNotEmpty())
        }
    }

    override suspend fun petsAreRegistered(int: Int) {
        assertEquals(int, petCount)
    }
}
