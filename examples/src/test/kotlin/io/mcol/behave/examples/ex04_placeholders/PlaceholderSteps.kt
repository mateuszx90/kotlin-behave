/**
 * ## Example 4: All Built-in Parameter Types
 *
 * Demonstrates how values in feature text are auto-detected by KSP:
 *
 * | Value in feature      | Auto-detected as | Kotlin Type |
 * |-----------------------|------------------|-------------|
 * | `"quoted text"`       | `{string}`       | `String`    |
 * | `25` (integer)        | `{int}`          | `Int`       |
 * | `98.5` (decimal)      | `{double}`       | `Double`    |
 * | `<variable>` (outline)| `{word}`         | `String`    |
 *
 * For `{boolean}` and `{long}`, use explicit `{placeholder}` syntax in step text.
 * The Scenario Outline shows how `<variable>` tokens produce `{word}` at runtime.
 */
package io.mcol.behave.examples.ex04_placeholders

import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/04_explicit_placeholders.feature")
class PlaceholderSteps : PlaceholderStepsSpec {

    private val form = mutableMapOf<String, Any>()

    // "name" and "Alice" auto-detected as {string} → String
    override suspend fun theFormFieldHasValue(string0: String, string1: String) {
        form[string0] = string1
    }

    // 25 auto-detected as {int} → Int
    override suspend fun iSetTheAgeTo(int: Int) {
        form["age"] = int
    }

    // 98.5 auto-detected as {double} → Double
    override suspend fun iSetTheScoreTo(double: Double) {
        form["score"] = double
    }

    override suspend fun theFormIsValid() {
        check(form.isNotEmpty()) { "Form is empty" }
    }

    // <flag> from Scenario Outline → {word} → String
    override suspend fun iSetThePremiumFlagTo(flag: String) {
        form["premium"] = flag.toBooleanStrict()
    }
}
