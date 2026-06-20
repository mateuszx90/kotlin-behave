package io.mcol.behave.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationTest {

    // region checkDivergence

    @Test
    fun `checkDivergence passes when step is in mixin registry`() {
        val check = Validation.checkDivergence(
            methodName = "iLogInAs",
            originalKeyword = "Given",
            originalText = "I log in as \"alice\"",
            inheritedMethodNames = setOf("iLogInAs"),
            sharers = setOf("com.foo.A", "com.foo.B"),
            thisClassName = "com.foo.A",
            hasDivergentAnnotation = false,
        )
        assertNull(check.errorMessage)
    }

    @Test
    fun `checkDivergence passes when step is unique to this feature`() {
        val check = Validation.checkDivergence(
            methodName = "iSelectAnExoticOption",
            originalKeyword = "When",
            originalText = "I select an exotic option",
            inheritedMethodNames = emptySet(),
            sharers = setOf("com.foo.A"),
            thisClassName = "com.foo.A",
            hasDivergentAnnotation = false,
        )
        assertNull(check.errorMessage)
    }

    @Test
    fun `checkDivergence passes when shared and divergence is explicitly marked`() {
        val check = Validation.checkDivergence(
            methodName = "theUserIsLoggedIn",
            originalKeyword = "Given",
            originalText = "the user is logged in",
            inheritedMethodNames = emptySet(),
            sharers = setOf("com.foo.WebSearchSteps", "com.foo.MobileSearchSteps"),
            thisClassName = "com.foo.WebSearchSteps",
            hasDivergentAnnotation = true,
        )
        assertNull(check.errorMessage)
    }

    @Test
    fun `checkDivergence errors when shared and not marked`() {
        val check = Validation.checkDivergence(
            methodName = "theUserIsLoggedIn",
            originalKeyword = "Given",
            originalText = "the user is logged in",
            inheritedMethodNames = emptySet(),
            sharers = setOf("com.foo.WebSearchSteps", "com.foo.MobileSearchSteps"),
            thisClassName = "com.foo.WebSearchSteps",
            hasDivergentAnnotation = false,
        )
        val message = assertNotNull(check.errorMessage)
        assertTrue("theUserIsLoggedIn" in message, "method name must appear in message")
        assertTrue("MobileSearchSteps" in message, "other sharer must be listed")
        assertTrue("@StepsMixin" in message, "mixin remediation must be mentioned")
        assertTrue("@DivergentStep" in message, "divergent annotation remediation must be mentioned")
    }

    @Test
    fun `checkDivergence message lists all other sharers alphabetically`() {
        val check = Validation.checkDivergence(
            methodName = "iSearchFor",
            originalKeyword = "When",
            originalText = "I search for \"x\"",
            inheritedMethodNames = emptySet(),
            sharers = setOf("com.z.Zeta", "com.a.Alpha", "com.m.Mid"),
            thisClassName = "com.m.Mid",
            hasDivergentAnnotation = false,
        )
        val message = assertNotNull(check.errorMessage)
        val alphaIdx = message.indexOf("com.a.Alpha")
        val zetaIdx = message.indexOf("com.z.Zeta")
        assertTrue(alphaIdx in 0..<zetaIdx, "sharers should be sorted: '$message'")
        assertTrue("com.m.Mid" !in message, "the current class should NOT be listed as a sharer")
    }

    // endregion

    // region checkMixinClash

    @Test
    fun `checkMixinClash registers a brand-new method`() {
        val check = Validation.checkMixinClash(
            methodName = "iLogInAs",
            paramTypes = listOf("String"),
            newOwnerQualifiedName = "com.foo.SessionMixin",
            priorOwnerQualifiedName = null,
        )
        assertNull(check.errorMessage)
        assertTrue(check.shouldRegister)
    }

    @Test
    fun `checkMixinClash skips a re-declaration by the same owner`() {
        val check = Validation.checkMixinClash(
            methodName = "iLogInAs",
            paramTypes = listOf("String"),
            newOwnerQualifiedName = "com.foo.SessionMixin",
            priorOwnerQualifiedName = "com.foo.SessionMixin",
        )
        assertNull(check.errorMessage)
        assertEquals(false, check.shouldRegister)
    }

    @Test
    fun `checkMixinClash errors when two different mixins declare the same method`() {
        val check = Validation.checkMixinClash(
            methodName = "iLogInAs",
            paramTypes = listOf("String"),
            newOwnerQualifiedName = "com.foo.SessionMixin",
            priorOwnerQualifiedName = "com.foo.AuthMixin",
        )
        val message = assertNotNull(check.errorMessage)
        assertTrue("iLogInAs(String)" in message, "method signature must appear")
        assertTrue("com.foo.AuthMixin" in message, "prior owner must be listed")
        assertTrue("com.foo.SessionMixin" in message, "new owner must be listed")
        assertEquals(false, check.shouldRegister, "must NOT register the clash (keep the first)")
    }

    @Test
    fun `checkMixinClash signature includes parameter types`() {
        val check = Validation.checkMixinClash(
            methodName = "iEnterText",
            paramTypes = listOf("String", "Int"),
            newOwnerQualifiedName = "com.foo.FormMixin",
            priorOwnerQualifiedName = "com.foo.InputMixin",
        )
        val message = assertNotNull(check.errorMessage)
        assertTrue("iEnterText(String, Int)" in message, "param types must appear in the signature")
    }

    // endregion
}
