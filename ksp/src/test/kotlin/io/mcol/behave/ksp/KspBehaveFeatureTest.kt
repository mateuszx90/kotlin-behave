package io.mcol.behave.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end coverage of the core `@BehaveFeature` generation: the spec interface, the
 * generated `val` + Kotest `*GherkinTest`, method-name normalisation, collision suffixing,
 * and the `generateTest = false` toggle.
 */
class KspBehaveFeatureTest {

    @Test
    fun `basic feature generates spec, steps val and GherkinTest`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "account.feature" to
                    """
                    Feature: Account
                        Scenario: Login
                            Given I am logged in
                            When I open settings
                            Then I see my profile
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "AccountSteps.kt",
                    """
                    package gen.account

                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("account.feature")
                    class AccountSteps : AccountStepsSpec {
                        override suspend fun iAmLoggedIn() {}
                        override suspend fun iOpenSettings() {}
                        override suspend fun iSeeMyProfile() {}
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("AccountStepsSpec")

        // Spec interface with one suspend method per unique step (keyword-agnostic names).
        assertContains(spec, "interface AccountStepsSpec")
        assertContains(spec, "suspend fun iAmLoggedIn()")
        assertContains(spec, "suspend fun iOpenSettings()")
        assertContains(spec, "suspend fun iSeeMyProfile()")

        // generateTest = true (default) → the wiring val + the Kotest test class.
        assertContains(spec, "val generatedAccountSteps = AccountStepsSpec.steps { AccountSteps() }")
        assertContains(spec, "class AccountGherkinTest : FreeSpec({")
        assertContains(spec, "gherkin(\"account.feature\", generatedAccountSteps)")
    }

    @Test
    fun `same generated name from different steps gets numeric collision suffixes`() {
        // Both steps strip to the SAME method name (iSeeTheResult), but their normalised text
        // differs by the trailing "!" so they are NOT deduplicated → numeric suffixing kicks in.
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "result.feature" to
                    """
                    Feature: Result
                        Scenario: Outcomes
                            Then I see the result
                            Then I see the result!
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "ResultSteps.kt",
                    """
                    package gen.result

                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("result.feature", generateTest = false)
                    class ResultSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("ResultStepsSpec")
        assertContains(spec, "suspend fun iSeeTheResult0()")
        assertContains(spec, "suspend fun iSeeTheResult1()")
    }

    @Test
    fun `generateTest=false omits the steps val and GherkinTest`() {
        val outcome = KspTestSupport.compile(
            features = mapOf(
                "ping.feature" to
                    """
                    Feature: Ping
                        Scenario: Ping
                            When I ping the server
                    """.trimIndent(),
            ),
            sources = listOf(
                KspTestSupport.source(
                    "PingSteps.kt",
                    """
                    package gen.ping

                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("ping.feature", generateTest = false)
                    class PingSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, outcome.exitCode, outcome.messages)
        val spec = outcome.generated("PingStepsSpec")
        assertContains(spec, "interface PingStepsSpec")
        assertContains(spec, "suspend fun iPingTheServer()")
        // No generated wiring val and no Kotest test class.
        assertFalse(spec.contains("GherkinTest"), "no *GherkinTest expected:\n$spec")
        assertFalse(spec.contains("val generated"), "no generated val expected:\n$spec")
    }

    @Test
    fun `missing feature file fails with the resolved path`() {
        val outcome = KspTestSupport.compile(
            features = emptyMap(), // intentionally do NOT write the feature file
            sources = listOf(
                KspTestSupport.source(
                    "GhostSteps.kt",
                    """
                    package gen.ghost

                    import io.mcol.behave.annotations.BehaveFeature

                    @BehaveFeature("does_not_exist.feature", generateTest = false)
                    class GhostSteps
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(outcome.exitCode != KotlinCompilation.ExitCode.OK, "build should fail")
        assertContains(outcome.messages, "Feature file not found")
        assertContains(outcome.messages, "does_not_exist.feature")
    }
}
