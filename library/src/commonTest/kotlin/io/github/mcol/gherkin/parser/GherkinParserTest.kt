package io.github.mcol.gherkin.parser

import io.github.mcol.gherkin.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class GherkinParserTest {

    @Test
    fun `model data classes hold values correctly`() {
        val step = Step(Keyword.GIVEN, "I have 5 words")
        val scenario = Scenario("basic", listOf(step))
        val feature = Feature("My feature", scenarios = listOf(scenario))

        assertEquals("My feature", feature.name)
        assertEquals(1, feature.scenarios.size)
        assertEquals(Keyword.GIVEN, feature.scenarios[0].steps[0].keyword)
        assertEquals("I have 5 words", feature.scenarios[0].steps[0].text)
    }
}
