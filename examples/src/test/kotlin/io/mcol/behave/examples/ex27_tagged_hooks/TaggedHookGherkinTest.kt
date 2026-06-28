package io.mcol.behave.examples.ex27_tagged_hooks

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

/** Runs the tag-scoped-hooks feature: the @db scenario sees the hook, the plain one does not. */
class TaggedHookGherkinTest :
    FreeSpec({
        gherkin("features/27_tagged_hooks.feature", taggedHookSteps)
    })
