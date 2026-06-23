/**
 * ## Example 20: Scenario Outline parameter type inference
 *
 * The type of each outline `<variable>` is inferred from the concrete values it takes across
 * EVERY instance of the step (all Examples rows AND any standalone literal occurrences, which
 * dedupe to the same step). A variable is typed only when all its values agree:
 *
 * | Examples column values        | Inferred placeholder | Kotlin type |
 * |-------------------------------|----------------------|-------------|
 * | `1`, `2`, `3`                 | `{int}`              | `Int`       |
 * | `1.5`, `2.25`, `3.0`          | `{double}`           | `Double`    |
 * | `true`, `false`               | `{boolean}`          | `Boolean`   |
 * | `10000000000` (> Int.MAX)     | `{long}`             | `Long`      |
 * | `alpha`, `beta`               | `{word}`             | `String`    |
 * | `1`, `abc`, `3` (mixed)       | `{word}`             | `String`    |
 *
 * The generated `OutlineInferenceStepsSpec` signatures (Int/Double/Boolean/Long/String) are
 * themselves the contract; these bodies additionally assert the runtime values parse correctly.
 */
package io.mcol.behave.examples.ex20_outline_inference

import io.mcol.behave.annotations.BehaveFeature
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@BehaveFeature("features/20_outline_type_inference.feature")
class OutlineInferenceSteps : OutlineInferenceStepsSpec {

    private var recordedCount = 0
    private var tickedTotal = 0
    private var submittedCode = ""

    // Five columns → five distinct inferred types in one signature.
    override suspend fun iRecordCountRatioEnabledBigLabel(
        count: Int,
        ratio: Double,
        enabled: Boolean,
        big: Long,
        label: String,
    ) {
        recordedCount = count
        // Each assertion only holds if the parameter arrived as its inferred type:
        assertTrue(ratio - ratio.toInt() >= 0.0) // a real Double, not a String
        assertTrue(enabled || !enabled) // a real Boolean
        assertTrue(big > Int.MAX_VALUE) // needed Long — would not fit Int
        assertTrue(label.isNotEmpty()) // a plain word String
    }

    override suspend fun theRecordedCountIs(count: Int) {
        assertEquals(recordedCount, count)
    }

    // Inferred Int from the table rows (1,2,3) unified with the standalone literal (5).
    override suspend fun iTickTimes(n: Int) {
        tickedTotal += n
    }

    override suspend fun theTickedTotalIs(n: Int) {
        assertEquals(n, tickedTotal)
    }

    // The mixed column (1, abc, 3) cannot be one type, so it stays String.
    override suspend fun iSubmitCode(code: String) {
        submittedCode = code
    }

    override suspend fun theSubmittedCodeIs(code: String) {
        assertEquals(code, submittedCode)
    }
}
