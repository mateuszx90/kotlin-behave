package io.mcol.behave.examples.ex24_outline_substitution

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class OutlineSubstitutionGherkinTest :
    FreeSpec({
        gherkin("features/24_outline_substitution.feature", outlineSubstitutionSteps)
    })
