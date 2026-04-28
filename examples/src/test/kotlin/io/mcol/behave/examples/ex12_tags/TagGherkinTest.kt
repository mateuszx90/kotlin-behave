package io.mcol.behave.examples.ex12_tags

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

// KSP generates TagGherkinTest (runs all scenarios) and generatedTagSteps.
// These additional test classes demonstrate tag filtering with the generated steps val.

/** Runs only `@smoke` scenarios. */
class TagSmokeGherkinTest :
    FreeSpec({
        gherkin("features/12_tags.feature", generatedTagSteps, tags = "@smoke")
    })

/** Runs `@smoke` but excludes `@slow`. */
class TagQuickGherkinTest :
    FreeSpec({
        gherkin("features/12_tags.feature", generatedTagSteps, tags = "@smoke and not @slow")
    })

/** Complex expression: `(@smoke or @critical) and not @wip`. */
class TagComplexGherkinTest :
    FreeSpec({
        gherkin(
            "features/12_tags.feature",
            generatedTagSteps,
            tags = "(@smoke or @critical) and not @wip",
        )
    })
