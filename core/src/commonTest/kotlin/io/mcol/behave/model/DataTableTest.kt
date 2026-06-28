package io.mcol.behave.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataTableTest {
    private val table = DataTable(
        listOf(
            mapOf("name" to "Alice", "age" to "30"),
            mapOf("name" to "Bob", "age" to "25"),
        ),
    )

    @Test
    fun `headers come from the first row in declaration order`() {
        assertEquals(listOf("name", "age"), table.headers)
    }

    @Test
    fun `asMaps returns the rows`() {
        assertEquals(table.rows, table.asMaps())
    }

    @Test
    fun `column returns the values of one column`() {
        assertEquals(listOf("Alice", "Bob"), table.column("name"))
        assertEquals(listOf("30", "25"), table.column("age"))
    }

    @Test
    fun `rowsAsLists keeps header order`() {
        assertEquals(listOf(listOf("Alice", "30"), listOf("Bob", "25")), table.rowsAsLists())
    }

    @Test
    fun `asMatrix prepends the header row`() {
        assertEquals(
            listOf(listOf("name", "age"), listOf("Alice", "30"), listOf("Bob", "25")),
            table.asMatrix(),
        )
    }

    @Test
    fun `transpose turns columns into rows`() {
        assertEquals(
            listOf(listOf("name", "Alice", "Bob"), listOf("age", "30", "25")),
            table.transpose(),
        )
    }

    @Test
    fun `asMap maps the first column to the second`() {
        val kv = DataTable(
            listOf(
                mapOf("key" to "host", "value" to "localhost"),
                mapOf("key" to "port", "value" to "8080"),
            ),
        )
        assertEquals(mapOf<String?, String?>("host" to "localhost", "port" to "8080"), kv.asMap())
    }

    @Test
    fun `asMap rejects tables that are not two columns`() {
        val threeCols = DataTable(listOf(mapOf("a" to "1", "b" to "2", "c" to "3")))
        assertFailsWith<IllegalArgumentException> { threeCols.asMap() }
    }

    @Test
    fun `empty table yields empty accessors`() {
        val empty = DataTable(emptyList())
        assertEquals(emptyList(), empty.headers)
        assertEquals(emptyList<String?>(), empty.asMatrix().flatten())
    }

    // region key-value / transposed table → object ----------------------------

    // A vertical key→value table: as the parser produces it, the first grid row is stored as the
    // header keys, so each row's keys are [field, firstValue] and the body holds the rest.
    private val verticalTable = DataTable(
        listOf(
            mapOf("name" to "age", "Alice" to "30"),
            mapOf("name" to "email", "Alice" to "alice@example.com"),
        ),
    )

    @Test
    fun `keyValue reads a vertical two-column table including the header row`() {
        assertEquals(
            mapOf<String, String?>("name" to "Alice", "age" to "30", "email" to "alice@example.com"),
            verticalTable.keyValue(),
        )
    }

    @Test
    fun `toObject maps a vertical table to a data class`() {
        data class Person(val name: String, val age: Int, val email: String)

        val person = verticalTable.toObject { m ->
            Person(m["name"] ?: "", (m["age"] ?: "0").toInt(), m["email"] ?: "")
        }
        assertEquals(Person("Alice", 30, "alice@example.com"), person)
    }

    @Test
    fun `keyValue rejects tables that are not two columns`() {
        val threeWide = DataTable(listOf(mapOf("a" to "1", "b" to "2", "c" to "3")))
        assertFailsWith<IllegalArgumentException> { threeWide.keyValue() }
    }

    // endregion

    // region diff -------------------------------------------------------------

    @Test
    fun `diff passes for identical tables`() {
        table.diff(DataTable(table.rows))
    }

    @Test
    fun `diff throws with a plus-minus report on mismatch`() {
        val actual = DataTable(
            listOf(
                mapOf("name" to "Alice", "age" to "30"),
                mapOf("name" to "Bob", "age" to "26"), // age changed 25 -> 26
            ),
        )
        val e = assertFailsWith<TableDiffException> { table.diff(actual) }
        val msg = e.message ?: ""
        assertContains(msg, "- | Bob | 25 |") // expected row missing from actual
        assertContains(msg, "+ | Bob | 26 |") // extra row present in actual
        assertContains(msg, "  | Alice | 30 |") // unchanged row kept as context
    }

    // endregion
}
