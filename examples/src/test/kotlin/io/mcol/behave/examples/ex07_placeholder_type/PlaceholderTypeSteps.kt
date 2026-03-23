/**
 * ## Example 7: Custom Type — Placeholder Mode
 *
 * Demonstrates:
 * - `@BehaveType(placeholder = "xxx")` maps a single `{xxx}` token to a domain type
 * - `@BehaveType` is `@Repeatable` — use multiple annotations for multiple custom types
 * - The parameter name in the generated method comes from the placeholder name
 * - At runtime, the matched string is converted via `parameterType()` in the step builder
 */
package io.mcol.behave.examples.ex07_placeholder_type

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.BehaveType

data class ButtonLabel(val value: String)
data class Screen(val value: String)

@BehaveFeature("features/07_custom_type_placeholder.feature")
@BehaveType(placeholder = "label", type = ButtonLabel::class)
@BehaveType(placeholder = "screen", type = Screen::class)
class PlaceholderTypeSteps : PlaceholderTypeStepsSpec {

    private var currentScreen = ""

    override suspend fun whenITapTheButton(label: ButtonLabel) {
        currentScreen = "navigated from ${label.value}"
    }

    override suspend fun thenISeeTheScreen(screen: Screen) {
        check(currentScreen.isNotEmpty()) { "No navigation happened" }
    }
}

val generatedPlaceholderTypeSteps = PlaceholderTypeStepsSpec.steps {
    PlaceholderTypeSteps()
}.also { steps ->
    steps.stepBuilder.parameterType<ButtonLabel>("label", "\\S+") { ButtonLabel(it) }
    steps.stepBuilder.parameterType<Screen>("screen", "\\S+") { Screen(it) }
}
