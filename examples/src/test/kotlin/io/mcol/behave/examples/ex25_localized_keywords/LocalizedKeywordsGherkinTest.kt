package io.mcol.behave.examples.ex25_localized_keywords

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class LocalizedKeywordsGherkinTest :
    FreeSpec({
        gherkin("features/25_localized_keywords.feature", localizedKeywordsSteps)
    })
