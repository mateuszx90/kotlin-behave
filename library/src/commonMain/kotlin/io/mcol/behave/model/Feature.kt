package io.mcol.behave.model

enum class Keyword { GIVEN, WHEN, THEN, AND, BUT }

data class DataTable(val rows: List<Map<String, String>>)

data class Step(
    val keyword: Keyword,
    val text: String,
    val dataTable: DataTable? = null,
)

data class Background(val steps: List<Step>)

data class Scenario(
    val name: String,
    val steps: List<Step>,
    val rows: List<Map<String, String>> = emptyList(),
)

data class Feature(
    val name: String,
    val background: Background? = null,
    val scenarios: List<Scenario>,
)
