package io.mcol.behave.examples.ex07_placeholder_type

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class PlaceholderTypeGherkinTest : FreeSpec({
    gherkin("features/07_custom_type_placeholder.feature", generatedPlaceholderTypeSteps)
})
