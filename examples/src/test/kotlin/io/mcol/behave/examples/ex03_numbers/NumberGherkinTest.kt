package io.mcol.behave.examples.ex03_numbers

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class NumberGherkinTest : FreeSpec({
    gherkin("features/03_number_parameters.feature", generatedNumberSteps)
})
