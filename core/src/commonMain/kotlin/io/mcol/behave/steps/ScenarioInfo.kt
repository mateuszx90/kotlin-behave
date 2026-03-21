package io.mcol.behave.steps

enum class ScenarioStatus { Passed, Failed, Pending, Skipped }

data class ScenarioInfo(
    val name: String,
    val tags: Set<String>,
    val status: ScenarioStatus,
)
