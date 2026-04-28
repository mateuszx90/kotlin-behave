package io.mcol.behave.examples.ex13_hooks

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

/** Uses manually wired steps with Before/After hooks (not the KSP-generated default). */
class HookWithLifecycleTest :
    FreeSpec({
        gherkin("features/13_hooks.feature", hookSteps)
    })
