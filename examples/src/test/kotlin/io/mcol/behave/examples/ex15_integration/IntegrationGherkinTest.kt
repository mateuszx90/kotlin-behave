package io.mcol.behave.examples.ex15_integration

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

// KSP generates IntegrationGherkinTest (runs all scenarios) and generatedIntegrationSteps.
// This additional test class demonstrates tag filtering with the generated steps val.

/** Same feature, but only `@smoke` scenarios. */
class IntegrationSmokeTest : FreeSpec({
    gherkin("features/15_full_integration.feature", generatedIntegrationSteps, tags = "@smoke")
})
