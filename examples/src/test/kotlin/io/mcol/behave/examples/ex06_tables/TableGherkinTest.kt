package io.mcol.behave.examples.ex06_tables

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class TableGherkinTest : FreeSpec({
    gherkin("features/06_data_tables.feature", generatedTableSteps)
})
