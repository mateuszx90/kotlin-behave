package io.mcol.behave.examples.ex11_cast

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class CastGherkinTest : FreeSpec({
    gherkin("features/11_behave_cast.feature", generatedCastSteps)
})
