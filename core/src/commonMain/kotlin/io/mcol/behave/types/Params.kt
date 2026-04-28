package io.mcol.behave.types

import io.mcol.behave.model.DataTable

@Suppress("UNCHECKED_CAST")
class Params(
    private val values: List<Any>,
    val dataTable: DataTable? = null,
) {
    operator fun <T> component1(): T = values[0] as T

    operator fun <T> component2(): T = values[1] as T

    operator fun <T> component3(): T = values[2] as T

    operator fun <T> component4(): T = values[3] as T

    operator fun <T> component5(): T = values[4] as T
}
