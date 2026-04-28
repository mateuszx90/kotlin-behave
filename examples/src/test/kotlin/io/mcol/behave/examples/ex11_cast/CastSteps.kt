/**
 * ## Example 11: @BehaveCast — Lossy Conversion
 *
 * Demonstrates:
 * - Step text `"I create a recipe with 4 portions"` auto-detects `4` as `{int}`
 * - But another scenario uses `2.5` — which doesn't match `{int}`
 * - Without `@BehaveCast`: KSP compile error:
 *   `Type mismatch: value '2.5' does not match {int}`
 * - With `@BehaveCast`: suppresses validation and generates widening code:
 *   - Step expression: `{int}` → `{double}` (so `2.5` matches at runtime)
 *   - Conversion: `.toInt()` (truncates the decimal)
 *
 * Widening map:
 * - `Int`   → receives as `Double`, converts via `.toInt()`
 * - `Long`  → receives as `Double`, converts via `.toLong()`
 * - `Float` → receives as `Double`, converts via `.toFloat()`
 */
package io.mcol.behave.examples.ex11_cast

import io.mcol.behave.annotations.BehaveCast
import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/11_behave_cast.feature")
class CastSteps : CastStepsSpec {

    private var portions = 0

    // @BehaveCast on the parameter: KSP widens {int} → {double} and generates .toInt()
    override suspend fun iCreateARecipeWithPortions(@BehaveCast int: Int) {
        portions = int
    }

    override suspend fun theRecipeHasPortions(@BehaveCast int: Int) {
        check(portions == int) { "Expected $int portions, got $portions" }
    }
}
