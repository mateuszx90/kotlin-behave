package io.mcol.behave.types

import io.mcol.behave.model.DataTable

@Suppress("UNCHECKED_CAST")
class Params(
    private val values: List<Any>,
    val dataTable: DataTable? = null,
) {
    operator fun <T> get(index: Int): T = values[index] as T
}
