package io.mcol.behave.runner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagFilterTest {

    private fun matches(expr: String, vararg tags: String) =
        parseTagFilter(expr).matches(tags.toSet())

    @Test
    fun `single tag matches when present`() {
        assertTrue(matches("@smoke", "@smoke", "@auth"))
    }

    @Test
    fun `single tag does not match when absent`() {
        assertFalse(matches("@smoke", "@auth"))
    }

    @Test
    fun `and requires both tags`() {
        assertTrue(matches("@smoke and @auth", "@smoke", "@auth"))
        assertFalse(matches("@smoke and @auth", "@smoke"))
    }

    @Test
    fun `or matches when either tag present`() {
        assertTrue(matches("@smoke or @auth", "@smoke"))
        assertTrue(matches("@smoke or @auth", "@auth"))
        assertFalse(matches("@smoke or @auth"))
    }

    @Test
    fun `not negates`() {
        assertTrue(matches("not @wip", "@smoke"))
        assertFalse(matches("not @wip", "@wip"))
    }

    @Test
    fun `and not combined`() {
        assertTrue(matches("@smoke and not @wip", "@smoke"))
        assertFalse(matches("@smoke and not @wip", "@smoke", "@wip"))
    }

    @Test
    fun `parentheses group correctly`() {
        assertTrue(matches("(@smoke or @auth) and not @slow", "@smoke"))
        assertFalse(matches("(@smoke or @auth) and not @slow", "@smoke", "@slow"))
        assertFalse(matches("(@smoke or @auth) and not @slow", "@other"))
    }

    @Test
    fun `operators are case insensitive`() {
        assertTrue(matches("@smoke AND NOT @wip", "@smoke"))
        assertTrue(matches("@smoke OR @auth", "@auth"))
    }
}
