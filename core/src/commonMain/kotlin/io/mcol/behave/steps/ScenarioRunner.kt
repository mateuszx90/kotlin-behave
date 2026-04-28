package io.mcol.behave.steps

/**
 * Implement this interface on your `@BehaveFeature` Steps class to provide
 * per-scenario setup for the KSP-generated test class.
 *
 * When KSP detects that a Steps class implements [ScenarioRunner], it generates:
 * ```kotlin
 * class XxxGherkinTest : FreeSpec({
 *     gherkin("features/xxx.feature", generatedXxxSteps) { ctx, run ->
 *         (ctx as ScenarioRunner).runScenario(ctx, run)
 *     }
 * })
 * ```
 *
 * Use Kotlin delegation (`by`) to share setup across multiple Steps classes:
 * ```kotlin
 * @BehaveFeature("features/collections.feature")
 * class CollectionsSteps : CollectionsStepsSpec, HasAppRobot,
 *     ScenarioRunner by ComposeScenarioRunner() {
 *     override lateinit var app: AppRobot
 * }
 * ```
 *
 * @see [runScenario]
 */
interface ScenarioRunner {
    /**
     * Called once per scenario. Set up the test environment, then call [run] to execute
     * Before hooks → Background steps → Scenario steps → After hooks.
     *
     * @param ctx The Steps instance (fresh per scenario). Cast to your interface
     *            (e.g. `HasAppRobot`) to inject dependencies.
     * @param run Blocking lambda that executes the scenario. Call exactly once.
     */
    fun runScenario(
        ctx: Any,
        run: () -> Unit,
    )
}
