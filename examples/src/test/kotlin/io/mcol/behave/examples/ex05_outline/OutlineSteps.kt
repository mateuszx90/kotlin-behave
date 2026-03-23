/**
 * ## Example 5: Scenario Outline
 *
 * Demonstrates:
 * - `Scenario Outline` with `<variable>` tokens and `Examples` table
 * - Quoted `"<answer>"` → `{string}` (not `{word}`)
 * - Unquoted `<type>` → `{word}`
 * - Each Examples row becomes a separate test scenario at runtime
 * - KSP preserves `<variable>` as templates — runtime parser expands them
 */
package io.mcol.behave.examples.ex05_outline

import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/05_scenario_outline.feature")
class OutlineSteps : OutlineStepsSpec {

    private var answer = ""
    private var feedbackType = ""

    // "<answer>" is quoted → {string}, param named from variable: `answer`
    override suspend fun whenITypeInTheAnswerField(answer: String) {
        this.answer = answer
    }

    override suspend fun andISubmitTheAnswer() {
        feedbackType = when (answer) {
            "dog" -> "correct"
            "dogg" -> "partial"
            else -> "incorrect"
        }
    }

    // <type> is unquoted → {word}, param named from variable: `type`
    override suspend fun thenISeeFeedback(type: String) {
        check(feedbackType == type) { "Expected '$type' feedback, got '$feedbackType'" }
    }
}

val generatedOutlineSteps = OutlineStepsSpec.steps { OutlineSteps() }
