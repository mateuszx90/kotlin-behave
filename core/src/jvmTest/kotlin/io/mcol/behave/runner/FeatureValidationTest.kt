package io.mcol.behave.runner

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The JVM runtime gate reuses the compile-time FeatureFileParser, so a malformed feature loaded at
 * runtime is rejected with the same structural diagnostics the build produces.
 */
class FeatureValidationTest {

    @Test
    fun `a well-formed feature has no structural errors`() {
        val errors = featureStructureErrors(
            """
            Feature: F
                Scenario: S
                    Given something happens
            """.trimIndent(),
        )
        assertTrue(errors.isEmpty(), "expected no errors, got $errors")
    }

    @Test
    fun `a missing Feature declaration is reported`() {
        val errors = featureStructureErrors(
            """
            Scenario: S
                Given something happens
            """.trimIndent(),
        )
        assertTrue(errors.any { "Missing Feature:" in it }, "got $errors")
    }

    @Test
    fun `a Scenario Outline variable with no Examples column is reported`() {
        val errors = featureStructureErrors(
            """
            Feature: F
                Scenario Outline: O
                    Given I use <thing>

                    Examples:
                        | other |
                        | 1     |
            """.trimIndent(),
        )
        assertTrue(errors.any { "missing columns for" in it }, "got $errors")
    }
}
