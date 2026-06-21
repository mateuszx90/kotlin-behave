package io.mcol.behave.types

import io.mcol.behave.model.DataTable
import io.mcol.behave.model.Feature
import io.mcol.behave.model.Keyword
import io.mcol.behave.model.Scenario
import io.mcol.behave.model.Step
import io.mcol.behave.runner.GherkinRunner
import io.mcol.behave.steps.steps
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CustomTypeTest {
    enum class Color { RED, GREEN, BLUE }

    data class Cat(
        val age: Int,
        val color: String,
    )

    class Ctx {
        var color: Color? = null
        var cats: List<Cat> = emptyList()
    }

    @Test
    fun `scalar parameterType is resolved in step expression`() = runTest {
        var captured: Color? = null
        val defs =
            steps(::Ctx) {
                parameterType<Color>("color", "[a-z]+") { Color.valueOf(it.uppercase()) }
                Given("the background is {color}") { params ->
                    val c = params[0] as Color
                    captured = c
                }
            }
        val feature =
            Feature(
                "F",
                scenarios =
                listOf(
                    Scenario("s", listOf(Step(Keyword.GIVEN, "the background is red"))),
                ),
            )
        GherkinRunner(defs).run(feature)
        assertEquals(Color.RED, captured)
    }

    @Test
    fun `table parameterType maps DataTable rows to objects`() = runTest {
        val captured = mutableListOf<Cat>()
        val defs =
            steps(::Ctx) {
                parameterType<Cat>("cat") { row -> Cat(row["age"]!!.toInt(), row["color"]!!) }
                Given("the following cats:") { params ->
                    val cats = params[0] as List<Cat>
                    captured.addAll(cats)
                }
            }
        val table =
            DataTable(
                listOf(
                    mapOf("age" to "3", "color" to "black"),
                    mapOf("age" to "5", "color" to "white"),
                ),
            )
        val feature =
            Feature(
                "F",
                scenarios =
                listOf(
                    Scenario("s", listOf(Step(Keyword.GIVEN, "the following cats:", table))),
                ),
            )
        GherkinRunner(defs).run(feature)
        assertEquals(2, captured.size)
        assertEquals(Cat(3, "black"), captured[0])
        assertEquals(Cat(5, "white"), captured[1])
    }

    @Test
    fun `TypeRegistry is instance-scoped - registries with same name do not interfere`() {
        val r1 = TypeRegistry()
        val r2 = TypeRegistry()
        r1.register("color", "[a-z]+") { "always-red" }
        r2.register("color", "[a-z]+") { "always-blue" }
        val c1 = r1.compile("the color is {color}")
        val c2 = r2.compile("the color is {color}")
        val m1 = c1.regex.matchEntire("the color is green")!!
        val m2 = c2.regex.matchEntire("the color is green")!!
        assertEquals("always-red", c1.convert(m1)[0])
        assertEquals("always-blue", c2.convert(m2)[0])
    }

    @Test
    fun `merge conflict throws when same name registered in both`() {
        val r1 = TypeRegistry()
        val r2 = TypeRegistry()
        r1.register("color", "[a-z]+") { it }
        r2.register("color", "[a-z]+") { it }
        assertFailsWith<IllegalStateException> { r1.merge(r2) }
    }

    @Test
    fun `custom type overrides builtin with same name`() {
        val r = TypeRegistry()
        r.register("int", """\d+""") { it.toInt() * 2 }
        val compiled = r.compile("value is {int}")
        val match = compiled.regex.matchEntire("value is 5")
        assertNotNull(match)
        assertEquals(10, compiled.convert(match)[0])
    }

    @Test
    fun `StepDefinitions plus merge conflict throws`() {
        val a =
            steps({ Unit }) {
                parameterType<String>("mytype", "[a-z]+") { it }
                Given("step a") { }
            }
        val b =
            steps({ Unit }) {
                parameterType<String>("mytype", "[a-z]+") { it }
                Given("step b") { }
            }
        assertFailsWith<IllegalStateException> { a + b }
    }

    @Test
    fun `StepDefinitions plus merges non-conflicting type registries`() = runTest {
        val log = mutableListOf<String>()
        val a =
            steps({ Unit }) {
                parameterType<String>("typeA", "[a-z]+") { it }
                Given("step a with {typeA}") { params ->
                    val v = params[0] as String
                    log.add("a:$v")
                }
            }
        val b =
            steps({ Unit }) {
                parameterType<String>("typeB", "[a-z]+") { it }
                Given("step b with {typeB}") { params ->
                    val v = params[0] as String
                    log.add("b:$v")
                }
            }
        val combined = a + b
        val feature =
            Feature(
                "F",
                scenarios =
                listOf(
                    Scenario(
                        "s",
                        listOf(
                            Step(Keyword.GIVEN, "step a with hello"),
                            Step(Keyword.GIVEN, "step b with world"),
                        ),
                    ),
                ),
            )
        GherkinRunner(combined).run(feature)
        assertEquals(listOf("a:hello", "b:world"), log)
    }
}
