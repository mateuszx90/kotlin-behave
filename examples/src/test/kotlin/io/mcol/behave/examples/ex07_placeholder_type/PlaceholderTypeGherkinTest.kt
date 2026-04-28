package io.mcol.behave.examples.ex07_placeholder_type

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

/** Uses custom-wired steps with parameterType registration (not the KSP-generated default). */
class PlaceholderTypeWithCustomTypesTest : FreeSpec({
    gherkin("features/07_custom_type_placeholder.feature", placeholderTypeStepsWithCustomTypes)
})
