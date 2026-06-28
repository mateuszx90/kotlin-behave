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

    /**
     * A *vertical* two-column table (no header row) as a field→value map: each grid row's first
     * cell is a field name and the second its value. Unlike [asMap], the parser's "header" row is
     * treated as data, so every pair is included. Use for key-value tables like:
     *
     * ```gherkin
     *   | name  | Alice |
     *   | age   | 30    |
     * ```
     *
     * Requires every row to have exactly two columns.
     */
    fun keyValue(): Map<String, String?> {
        val matrix = asMatrix()
        require(matrix.isNotEmpty() && matrix.all { it.size == 2 }) {
            "keyValue() requires a two-column table, got widths ${matrix.map { it.size }}"
        }
        return matrix.associate { (it[0] ?: "") to it[1] }
    }

    /** Maps a vertical key→value table ([keyValue]) to a domain object via [factory]. */
    fun <T> toObject(factory: (Map<String, String?>) -> T): T = factory(keyValue())

    /**
     * Asserts [actual] matches this (expected) table row-for-row, comparing header-keyed rows.
     * Throws [TableDiffException] with a `+`/`-` diff on mismatch — Cucumber's `DataTable.diff`.
     */
    fun diff(actual: DataTable) {
        if (rows == actual.rows) return
        val cols = (headers + actual.headers).distinct()
        val report = buildString {
            appendLine("DataTable mismatch:")
            appendLine("  ${cols.joinToString(" | ", "| ", " |")}")
            for (row in rows) {
                val marker = if (row in actual.rows) " " else "-"
                appendLine("$marker ${cols.joinToString(" | ", "| ", " |") { row[it] ?: "" }}")
            }
            for (row in actual.rows) {
                if (row !in rows) appendLine("+ ${cols.joinToString(" | ", "| ", " |") { row[it] ?: "" }}")
            }
        }.trimEnd()
        throw TableDiffException(report)
    }
}

/** Thrown by [DataTable.diff] when the expected and actual tables differ. */
class TableDiffException(
    message: String,
) : AssertionError(message)

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
