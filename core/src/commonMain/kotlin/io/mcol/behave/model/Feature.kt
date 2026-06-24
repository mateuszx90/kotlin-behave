package io.mcol.behave.model

enum class Keyword { GIVEN, WHEN, THEN, AND, BUT }

data class DataTable(
    val rows: List<Map<String, String?>>,
) {
    /** Column names in declaration order, taken from the first row. Empty when the table has no rows. */
    val headers: List<String> get() = rows.firstOrNull()?.keys?.toList() ?: emptyList()

    /** Rows as header→value maps (identity — provided for symmetry with the other accessors). */
    fun asMaps(): List<Map<String, String?>> = rows

    /** All values of column [name] across the data rows, in row order. */
    fun column(name: String): List<String?> = rows.map { it[name] }

    /** Data rows as ordered value lists, each in header order. */
    fun rowsAsLists(): List<List<String?>> = rows.map { row -> headers.map { row[it] } }

    /** The header row followed by every data row, all as ordered value lists. */
    fun asMatrix(): List<List<String?>> = listOf(headers) + rowsAsLists()

    /** [asMatrix] transposed: each inner list is a column — its header first, then that column's values. */
    fun transpose(): List<List<String?>> {
        val matrix = asMatrix()
        val cols = headers.size
        return (0 until cols).map { c -> matrix.map { it.getOrNull(c) } }
    }

    /**
     * A two-column table as an ordered map from the first column's value to the second.
     * Requires exactly two columns.
     */
    fun asMap(): Map<String?, String?> {
        require(headers.size == 2) { "asMap() requires a two-column table, got ${headers.size}: $headers" }
        val (k, v) = headers
        return rows.associate { it[k] to it[v] }
    }
}

data class Step(
    val keyword: Keyword,
    val text: String,
    val dataTable: DataTable? = null,
    val docString: String? = null,
    /** Optional content type declared after the doc string's opening fence, e.g. ```json -> "json". */
    val docStringContentType: String? = null,
)

data class Background(
    val steps: List<Step>,
)

data class Scenario(
    val name: String,
    val steps: List<Step>,
    val rows: List<Map<String, String>> = emptyList(),
    val tags: Set<String> = emptySet(),
)

data class Feature(
    val name: String,
    val background: Background? = null,
    val scenarios: List<Scenario>,
    val tags: Set<String> = emptySet(),
)
