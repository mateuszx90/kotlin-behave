package io.mcol.behave.ksp

import io.mcol.behave.gherkin.GherkinTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The KSP value validator must mirror the runtime step matcher exactly, or it either rejects values
 * the runtime accepts (false positive) or misses ones it can't parse. Both now read their scalar
 * regexes from [GherkinTypes.builtinValuePatterns], so these tests pin that no-drift contract and
 * the previously-too-strict `{double}` rule.
 */
class TypeValidatorPatternTest {

    @Test
    fun `scalar validator patterns match the shared source of truth`() {
        for ((name, pattern) in GherkinTypes.builtinValuePatterns) {
            assertEquals(pattern, TypeValidator.typeValidationPatterns.getValue(name).pattern, "drift for {$name}")
        }
    }

    @Test
    fun `double accepts a bare integer like the runtime does`() {
        val double = TypeValidator.typeValidationPatterns.getValue("double")
        // Runtime pattern is -?\d+\.?\d* — it accepts "5" and "5." as doubles. The validator must too.
        assertTrue(double.matches("5"), "runtime accepts bare integer as double")
        assertTrue(double.matches("5."), "runtime accepts trailing-dot as double")
        assertTrue(double.matches("5.5"))
        assertTrue(double.matches("-2.0"))
        assertFalse(double.matches("abc"))
        assertFalse(double.matches("5.5.5"))
    }
}
