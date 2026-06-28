package io.mcol.behave.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BehaveLintTest {

    private val validFeature = """
        Feature: Login
          Scenario: Sign in
            Given I am logged in
            Then I see the dashboard
    """.trimIndent()

    @Test
    fun `clean features and fully-referenced steps produce no findings`() {
        val source = """
            class LoginSteps : LoginStepsSpec {
                override suspend fun iAmLoggedIn() {}
                override suspend fun iSeeTheDashboard() {}
            }
        """.trimIndent()
        val result = BehaveLint.analyze(mapOf("login.feature" to validFeature), listOf(source))
        assertFalse(result.hasParseErrors)
        assertTrue(result.deadSteps.isEmpty(), "expected no dead steps, got ${result.deadSteps}")
    }

    @Test
    fun `a step method no feature references is reported as dead`() {
        val source = """
            class LoginSteps : LoginStepsSpec {
                override suspend fun iAmLoggedIn() {}
                override suspend fun iSeeTheDashboard() {}
                override suspend fun iClickAGhostButton() {}
            }
        """.trimIndent()
        val result = BehaveLint.analyze(mapOf("login.feature" to validFeature), listOf(source))
        assertEquals(listOf("iClickAGhostButton"), result.deadSteps)
    }

    @Test
    fun `unparseable feature is reported with file and line`() {
        val broken = """
            Scenario: no feature header
              Given something
        """.trimIndent()
        val result = BehaveLint.analyze(mapOf("broken.feature" to broken), emptyList())
        assertTrue(result.hasParseErrors)
        assertEquals("broken.feature", result.parseErrors.first().file)
        assertContains(result.parseErrors.first().message, "Feature:")
    }

    @Test
    fun `lifecycle hook overrides are never flagged as dead`() {
        val source = """
            class LoginSteps : LoginStepsSpec, ScenarioHooks {
                override suspend fun iAmLoggedIn() {}
                override suspend fun iSeeTheDashboard() {}
                override suspend fun beforeScenario() {}
                override suspend fun afterScenario(info: ScenarioInfo) {}
            }
        """.trimIndent()
        val result = BehaveLint.analyze(mapOf("login.feature" to validFeature), listOf(source))
        assertTrue(result.deadSteps.isEmpty(), "lifecycle hooks must not be dead, got ${result.deadSteps}")
    }

    @Test
    fun `declaredStepMethods extracts override suspend fun names`() {
        val source = "override suspend fun fooBar() {}\n  override  suspend  fun baz(x: Int) {}"
        assertEquals(listOf("fooBar", "baz"), BehaveLint.declaredStepMethods(source))
    }
}
