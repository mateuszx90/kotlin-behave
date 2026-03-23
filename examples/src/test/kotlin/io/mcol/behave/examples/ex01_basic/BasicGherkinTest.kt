package io.mcol.behave.examples.ex01_basic

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class BasicGherkinTest : FreeSpec({
    gherkin("features/01_basic_steps.feature", generatedBasicSteps)
})
