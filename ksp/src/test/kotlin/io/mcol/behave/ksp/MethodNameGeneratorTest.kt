package io.mcol.behave.ksp

import kotlin.test.Test
import kotlin.test.assertEquals

class MethodNameGeneratorTest {

    @Test
    fun `generates basic camelCase method name`() {
        assertEquals("givenIAmOnTheLoginPage", MethodNameGenerator.generate("Given", "I am on the login page"))
    }

    @Test
    fun `strips placeholder tokens from name`() {
        assertEquals("whenIEnterAsUsername", MethodNameGenerator.generate("When", "I enter {string} as username"))
    }

    @Test
    fun `strips variable tokens from name`() {
        assertEquals("givenIAmLoggedInAs", MethodNameGenerator.generate("Given", "I am logged in as <role>"))
    }

    @Test
    fun `strips trailing colon`() {
        assertEquals("givenTheFollowingCats", MethodNameGenerator.generate("Given", "the following cats:"))
    }

    @Test
    fun `handles int placeholder stripped`() {
        assertEquals("thenIHaveItems", MethodNameGenerator.generate("Then", "I have {int} items"))
    }

    @Test
    fun `strips quoted literal strings from method name`() {
        assertEquals("thenISeeTheQuestionWord", MethodNameGenerator.generate("Then", "I see the question word \"pies\""))
    }

    @Test
    fun `strips quoted outline variable from method name`() {
        assertEquals("whenITypeInTheAnswerField", MethodNameGenerator.generate("When", "I type \"<answer>\" in the answer field"))
    }

    @Test
    fun `strips quoted literal for tap step`() {
        assertEquals("whenITap", MethodNameGenerator.generate("When", "I tap \"Cancel\""))
    }

    @Test
    fun `handles And keyword`() {
        assertEquals("andIClickTheButton", MethodNameGenerator.generate("And", "I click the {label} button"))
    }

    @Test
    fun `handles But keyword`() {
        assertEquals("butIDoNotSeeErrors", MethodNameGenerator.generate("But", "I do not see errors"))
    }

    @Test
    fun `collision resolution adds numeric suffixes`() {
        val steps = listOf(
            "Given" to "I have {int} items",
            "Given" to "I have {string} items",
        )
        val names = MethodNameGenerator.resolveCollisions(steps)
        assertEquals("givenIHaveItems0", names[0])
        assertEquals("givenIHaveItems1", names[1])
    }

    @Test
    fun `no collision when names differ`() {
        val steps = listOf(
            "Given" to "I am on the login page",
            "When" to "I click login",
        )
        val names = MethodNameGenerator.resolveCollisions(steps)
        assertEquals("givenIAmOnTheLoginPage", names[0])
        assertEquals("whenIClickLogin", names[1])
    }

    @Test
    fun `non-collision steps are not suffixed`() {
        val steps = listOf(
            "Given" to "step A",
            "Given" to "step A",
            "Given" to "step B",
        )
        val names = MethodNameGenerator.resolveCollisions(steps)
        assertEquals("givenStepA0", names[0])
        assertEquals("givenStepA1", names[1])
        assertEquals("givenStepB", names[2])
    }
}
