package io.mcol.behave.examples.ex23_gherkin_keywords

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class KeywordGherkinTest :
    FreeSpec({
        gherkin("features/23_gherkin_keywords.feature", keywordSteps)
    })
