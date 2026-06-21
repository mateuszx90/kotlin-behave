package io.mcol.behave.types

import io.mcol.behave.model.DataTable

@Suppress("UNCHECKED_CAST")
class Params(
    private val values: List<Any>,
    val dataTable: DataTable? = null,
) {
    operator fun <T> get(index: Int): T = values[index] as T

    operator fun <T> component1(): T = this[0]

    operator fun <T> component2(): T = this[1]

    operator fun <T> component3(): T = this[2]

    operator fun <T> component4(): T = this[3]

    operator fun <T> component5(): T = this[4]
}
