/**
 * ## Example 9: DataTable with full type mapping
 *
 * Demonstrates:
 * - `@BehaveType(type = WordRow::class)` covering ALL columns → `List<WordRow>` directly
 *   (no auto-generated Row class — the user's type is used as-is)
 * - DataTable combined with an inline `{string}` parameter in the same step
 * - Multiple DataTable steps sharing the same type mapping
 * - Background step with DataTable (shared across all scenarios)
 * - Unmapped DataTable (inventory) → auto-generated Row class with all-String fields
 *
 * Key convention: when `@BehaveType` covers ALL columns of a DataTable, the type name
 * must end with `Row` (e.g. `WordRow`) for KSP to generate inline mapping code.
 * If the name doesn't end with `Row`, KSP generates a raw cast instead.
 */
package io.mcol.behave.examples.ex09_table_mapping

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.BehaveType
import kotlin.test.assertEquals

/** Covers ALL columns of the vocabulary DataTable (polish, english). */
data class WordRow(val polish: String, val english: String)

@BehaveFeature("features/09_datatable_full_mapping.feature")
@BehaveType(type = WordRow::class)
class TableMappingSteps : TableMappingStepsSpec {

    private val words = mutableListOf<WordRow>()
    private val collections = mutableMapOf<String, MutableList<WordRow>>()
    private val inventory = mutableListOf<GivenTheFollowingInventoryRow>()

    // --- Background: DataTable → List<WordRow> (full mapping, no Row class generated) ---

    override suspend fun givenTheFollowingVocabulary(rows: List<WordRow>) {
        words.addAll(rows)
    }

    override suspend fun thenWordsAreLoaded(int: Int) {
        assertEquals(int, words.size)
    }

    // --- DataTable reusing the same WordRow type ---

    override suspend fun whenIAddTheFollowingWords(rows: List<WordRow>) {
        words.addAll(rows)
    }

    // --- DataTable + inline string param in the same step ---
    // Generated: whenIAssignTheFollowingWordsTo(string: String, rows: List<WordRow>)

    override suspend fun givenACollectionNamed(string: String) {
        collections[string] = mutableListOf()
    }

    override suspend fun whenIAssignTheFollowingWordsTo(string: String, rows: List<WordRow>) {
        collections.getOrPut(string) { mutableListOf() }.addAll(rows)
    }

    override suspend fun thenTheCollectionHasWords(string: String, int: Int) {
        assertEquals(int, collections[string]?.size ?: 0)
    }

    // --- Unmapped DataTable → auto-generated GivenTheFollowingInventoryRow (all String) ---

    override suspend fun givenTheFollowingInventory(rows: List<GivenTheFollowingInventoryRow>) {
        inventory.addAll(rows)
    }

    override suspend fun thenTheTotalInventoryValueIs(double: Double) {
        val total = inventory.sumOf {
            it.quantity.toInt() * it.price.toDouble()
        }
        assertEquals(double, total, 0.01)
    }
}

val generatedTableMappingSteps = TableMappingStepsSpec.steps { TableMappingSteps() }
