package io.mcol.behave.types

@Suppress("UNCHECKED_CAST")
class Params(private val values: List<Any>) {
    operator fun <T> component1(): T = values[0] as T
    operator fun <T> component2(): T = values[1] as T
    operator fun <T> component3(): T = values[2] as T
    operator fun <T> component4(): T = values[3] as T
    operator fun <T> component5(): T = values[4] as T
}
