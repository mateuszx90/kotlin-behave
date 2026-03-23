package io.mcol.behave.examples.ex08_multi_outline

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class MultiOutlineGherkinTest : FreeSpec({
    gherkin("features/08_multi_column_outline.feature", generatedMultiOutlineSteps)
})
