package io.mcol.behave.examples.ex05_outline

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class OutlineGherkinTest : FreeSpec({
    gherkin("features/05_scenario_outline.feature", generatedOutlineSteps)
})
