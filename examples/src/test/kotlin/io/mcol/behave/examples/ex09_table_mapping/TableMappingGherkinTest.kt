package io.mcol.behave.examples.ex09_table_mapping

import io.kotest.core.spec.style.FreeSpec
import io.mcol.behave.kotest.gherkin

class TableMappingGherkinTest : FreeSpec({
    gherkin("features/09_datatable_full_mapping.feature", generatedTableMappingSteps)
})
