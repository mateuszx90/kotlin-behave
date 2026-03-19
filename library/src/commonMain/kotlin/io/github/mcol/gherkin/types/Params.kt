package io.github.mcol.gherkin.types

class Params(private val values: List<Any>) {
    operator fun component1(): Any = values[0]
    operator fun component2(): Any = values[1]
    operator fun component3(): Any = values[2]
    operator fun component4(): Any = values[3]
    operator fun component5(): Any = values[4]
}
