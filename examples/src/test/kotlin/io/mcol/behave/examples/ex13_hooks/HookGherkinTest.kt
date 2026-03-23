package io.mcol.behave.examples.ex13_hooks

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class HookGherkinTest : FreeSpec({
    gherkin("features/13_hooks.feature", hookSteps)
})
