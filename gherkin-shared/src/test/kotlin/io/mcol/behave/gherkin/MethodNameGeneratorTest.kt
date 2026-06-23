package io.mcol.behave.gherkin

import kotlin.test.Test
import kotlin.test.assertEquals

class MethodNameGeneratorTest {
    @Test
    fun `generates basic camelCase method name without keyword`() {
        assertEquals("iAmOnTheLoginPage", MethodNameGenerator.generate("Given", "I am on the login page"))
    }

    @Test
    fun `strips placeholder tokens from name`() {
        assertEquals("iEnterAsUsername", MethodNameGenerator.generate("When", "I enter {string} as username"))
    }

    @Test
    fun `strips variable tokens from name`() {
        assertEquals("iAmLoggedInAs", MethodNameGenerator.generate("Given", "I am logged in as <role>"))
    }

    @Test
    fun `strips trailing colon`() {
        assertEquals("theFollowingCats", MethodNameGenerator.generate("Given", "the following cats:"))
    }

    @Test
    fun `handles int placeholder stripped`() {
        assertEquals("iHaveItems", MethodNameGenerator.generate("Then", "I have {int} items"))
    }

    @Test
    fun `strips quoted literal strings from method name`() {
        assertEquals("iSeeTheQuestionWord", MethodNameGenerator.generate("Then", "I see the question word \"pies\""))
    }

    @Test
    fun `strips quoted outline variable from method name`() {
        assertEquals("iTypeInTheAnswerField", MethodNameGenerator.generate("When", "I type \"<answer>\" in the answer field"))
    }

    @Test
    fun `strips quoted literal for tap step`() {
        assertEquals("iTap", MethodNameGenerator.generate("When", "I tap \"Cancel\""))
    }

    @Test
    fun `keyword is ignored in method name`() {
        val given = MethodNameGenerator.generate("Given", "I click the button")
        val whenResult = MethodNameGenerator.generate("When", "I click the button")
        val thenResult = MethodNameGenerator.generate("Then", "I click the button")
        assertEquals("iClickTheButton", given)
        assertEquals(given, whenResult, "Same text with different keywords should produce same method name")
        assertEquals(given, thenResult)
    }

    @Test
    fun `strips standalone number literals from method name`() {
        assertEquals(
            "iCreateARecipeWithPortionsNamed",
            MethodNameGenerator.generate("When", """I create a recipe with 4 portions named "Pasta""""),
        )
    }

    @Test
    fun `does not strip numbers embedded in words from method name`() {
        assertEquals(
            "iVisitStep2goWebsite",
            MethodNameGenerator.generate("When", "I visit step2go website"),
        )
    }

    @Test
    fun `collision resolution adds numeric suffixes`() {
        val steps =
            listOf(
                "Given" to "I have {int} items",
                "Given" to "I have {string} items",
            )
        val names = MethodNameGenerator.resolveCollisions(steps)
        assertEquals("iHaveItems0", names[0])
        assertEquals("iHaveItems1", names[1])
    }

    @Test
    fun `same text different keywords produces same name`() {
        val steps =
            listOf(
                "Given" to "I am on the login page",
                "When" to "I am on the login page",
            )
        val names = MethodNameGenerator.resolveCollisions(steps)
        assertEquals("iAmOnTheLoginPage0", names[0])
        assertEquals("iAmOnTheLoginPage1", names[1])
    }

    @Test
    fun `non-collision steps are not suffixed`() {
        val steps =
            listOf(
                "Given" to "step A",
                "Given" to "step A",
                "Given" to "step B",
            )
        val names = MethodNameGenerator.resolveCollisions(steps)
        assertEquals("stepA0", names[0])
        assertEquals("stepA1", names[1])
        assertEquals("stepB", names[2])
    }
}
