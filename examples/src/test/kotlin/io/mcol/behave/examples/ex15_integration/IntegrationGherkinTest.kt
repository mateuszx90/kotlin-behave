package io.mcol.behave.examples.ex15_integration

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

/** Full integration: runs all scenarios including Outline expansion. */
class IntegrationGherkinTest : FreeSpec({
    gherkin("features/15_full_integration.feature", generatedIntegrationSteps)
})

/** Same feature, but only `@smoke` scenarios. */
class IntegrationSmokeTest : FreeSpec({
    gherkin("features/15_full_integration.feature", generatedIntegrationSteps, tags = "@smoke")
})
