package io.mcol.behave.examples.ex10_typed_table

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class TypedTableGherkinTest : FreeSpec({
    gherkin("features/10_datatable_typed.feature", generatedTypedTableSteps)
})
