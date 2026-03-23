package io.mcol.behave.examples.ex02_strings

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class StringGherkinTest : FreeSpec({
    gherkin("features/02_string_parameters.feature", generatedStringSteps)
})
