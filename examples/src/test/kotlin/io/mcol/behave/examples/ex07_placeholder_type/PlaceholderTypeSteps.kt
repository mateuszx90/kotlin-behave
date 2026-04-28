/**
 * ## Example 7: Custom Type — Placeholder Mode
 *
 * Demonstrates:
 * - `@BehaveType(placeholder = "xxx")` maps a single `{xxx}` token to a domain type
 * - `@BehaveType` is `@Repeatable` — use multiple annotations for multiple custom types
 * - At runtime, the matched string is converted via `parameterType()` in the step builder
 *
 * Note: This example requires custom runtime type registration via `parameterType()`,
 * so it uses a manually wired val instead of the KSP-generated default.
 */
package io.mcol.behave.examples.ex07_placeholder_type

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.BehaveType

data class ButtonLabel(val value: String)
data class Screen(val value: String)

@BehaveFeature("features/07_custom_type_placeholder.feature", generateTest = false)
@BehaveType(placeholder = "label", type = ButtonLabel::class)
@BehaveType(placeholder = "screen", type = Screen::class)
class PlaceholderTypeSteps : PlaceholderTypeStepsSpec {

    private var currentScreen = ""

    override suspend fun iTapTheButton(label: ButtonLabel) {
        currentScreen = "navigated from ${label.value}"
    }

    override suspend fun iSeeTheScreen(screen: Screen) {
        check(currentScreen.isNotEmpty()) { "No navigation happened" }
    }
}

// Custom wiring with parameterType registration (overrides the KSP-generated default)
val placeholderTypeStepsWithCustomTypes = PlaceholderTypeStepsSpec.steps {
    PlaceholderTypeSteps()
}.also { steps ->
    steps.stepBuilder.parameterType<ButtonLabel>("label", "\\S+") { ButtonLabel(it) }
    steps.stepBuilder.parameterType<Screen>("screen", "\\S+") { Screen(it) }
}
