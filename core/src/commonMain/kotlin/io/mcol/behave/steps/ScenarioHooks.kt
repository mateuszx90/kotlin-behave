package io.mcol.behave.steps

/**
 * Implement on your Steps class to run setup before each scenario.
 * Runs after [ScenarioRunner] sets up the test environment (if present),
 * before Background steps.
 */
interface BeforeScenario {
    suspend fun beforeScenario()
}

/**
 * Implement on your Steps class to run cleanup after each scenario.
 * Runs after all steps complete, receives [ScenarioInfo] with name, tags, and status.
 */
interface AfterScenario {
    suspend fun afterScenario(info: ScenarioInfo)
}

/**
 * Convenience interface combining [BeforeScenario] and [AfterScenario]
 * with default no-op implementations. Override only what you need.
 */
interface ScenarioHooks : BeforeScenario, AfterScenario {
    override suspend fun beforeScenario() {}
    override suspend fun afterScenario(info: ScenarioInfo) {}
}
