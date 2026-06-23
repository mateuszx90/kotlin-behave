package io.mcol.behave.examples.ex22_lifecycle_hooks

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

/** Manually wired so the BeforeAll/AfterAll/BeforeStep/AfterStep hooks are registered. */
class LifecycleGherkinTest :
    FreeSpec({
        gherkin("features/22_lifecycle_hooks.feature", lifecycleSteps)
    })
