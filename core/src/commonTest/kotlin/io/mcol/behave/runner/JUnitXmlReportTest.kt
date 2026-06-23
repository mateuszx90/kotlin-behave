package io.mcol.behave.runner

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JUnitXmlReportTest {
    private val result = RunResult(
        featureName = "Login & <flow>",
        scenarios = listOf(
            ScenarioResult("passes", passed = true),
            ScenarioResult("fails", passed = false, error = RuntimeException("boom <x>"), failedStep = "I click"),
            ScenarioResult("pends", passed = false, pending = true, failedStep = "TODO step"),
            ScenarioResult("filtered", passed = false, skipped = true),
        ),
    )

    @Test
    fun `suite has correct counts`() {
        val xml = JUnitXmlReport.renderSuite(result)
        assertContains(xml, """tests="4"""")
        assertContains(xml, """failures="1"""")
        assertContains(xml, """skipped="2"""") // pending + tag-skipped
    }

    @Test
    fun `passed scenario is a self-closing testcase`() {
        val xml = JUnitXmlReport.renderSuite(result)
        assertContains(xml, """<testcase classname="Login &amp; &lt;flow&gt;" name="passes"/>""")
    }

    @Test
    fun `failed scenario carries a failure element with escaped message`() {
        val xml = JUnitXmlReport.renderSuite(result)
        assertContains(xml, """name="fails"""")
        assertContains(xml, "<failure")
        assertContains(xml, "boom &lt;x&gt;") // element text escaped
    }

    @Test
    fun `pending and filtered scenarios are skipped`() {
        val xml = JUnitXmlReport.renderSuite(result)
        assertEquals(2, Regex("<skipped").findAll(xml).count())
    }

    @Test
    fun `attribute special characters are escaped`() {
        val xml = JUnitXmlReport.renderSuite(result)
        assertContains(xml, """name="Login &amp; &lt;flow&gt;"""")
        assertTrue("<flow>" !in xml.substringBefore("</testsuite>").lineSequence().first())
    }

    @Test
    fun `document wraps suites in testsuites`() {
        val xml = JUnitXmlReport.render(listOf(result))
        assertContains(xml, "<?xml version=\"1.0\"")
        assertContains(xml, "<testsuites>")
        assertContains(xml, "</testsuites>")
    }
}
