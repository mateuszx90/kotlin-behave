package io.mcol.behave.examples.ex14_collisions

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class CollisionGherkinTest : FreeSpec({
    gherkin("features/14_method_name_collisions.feature", generatedCollisionSteps)
})
