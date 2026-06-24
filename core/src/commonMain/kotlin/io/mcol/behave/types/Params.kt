package io.mcol.behave.types

import io.mcol.behave.model.DataTable

@Suppress("UNCHECKED_CAST")
class Params(
    private val values: List<Any>,
    val dataTable: DataTable? = null,
    val docString: String? = null,
    /** Content type declared after the doc string fence (```json -> "json"); null when none given. */
    val docStringContentType: String? = null,
) {
    operator fun <T> get(index: Int): T = values[index] as T
}
