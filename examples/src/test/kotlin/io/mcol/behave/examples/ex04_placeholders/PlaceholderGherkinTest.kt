package io.mcol.behave.examples.ex04_placeholders

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class PlaceholderGherkinTest : FreeSpec({
    gherkin("features/04_explicit_placeholders.feature", generatedPlaceholderSteps)
})
