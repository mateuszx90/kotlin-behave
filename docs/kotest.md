# Kotest Integration

[← Back to README](../README.md)

`:kotest` renders a feature file as a [Kotest](https://kotest.io) `FreeSpec` test tree — one
leaf per scenario (or per step), with IDE reporting, tag filtering, and per-scenario setup.
Targets: JVM, JS, iOS, macOS, Linux.

- [Dependency](#dependency)
- [Basic usage](#basic-usage)
- [Per-scenario setup](#per-scenario-setup)
- [Step-level reporting](#step-level-reporting)
- [Tag filtering](#tag-filtering)
- [Hooks on the Steps class](#hooks-on-the-steps-class)

---

## Dependency

```kotlin
dependencies {
    testImplementation("io.mcol.kotlin-behave:core:0.1.0")
    testImplementation("io.mcol.kotlin-behave:kotest:0.1.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")   // JVM runner
}

tasks.withType<Test> { useJUnitPlatform() }
```

## Basic usage

Call `gherkin(...)` inside a `FreeSpec`. With KSP this whole class is generated for you; the
manual form is:

```kotlin
class CounterGherkinTest : FreeSpec({
    gherkin("features/counter.feature", counterSteps)
})
```

IDE test tree:

```
CounterGherkinTest
  Feature: Counter
    Scenario: increment once         ✓
    Scenario: increment twice        ✓
```

## Per-scenario setup

The `runScenario` overload wraps each scenario in your own harness — Compose UI test, a fresh
database, a transaction. It hands you the scenario `ctx` and a `run` lambda; call `run()`
where the steps should execute:

```kotlin
class CollectionsGherkinTest : FreeSpec({
    gherkin("features/collections.feature", collectionsSteps) { ctx, run ->
        runComposeUiTest {
            (ctx as CollectionsSteps).app = AppRobotImpl(this)
            setAppContent()
            run()                       // ← steps run here, inside the harness
        }
    }
})
```

```kotlin
fun <C> FreeSpec.gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    runScenario: suspend (ctx: C, run: suspend () -> Unit) -> Unit,
)
```

## Step-level reporting

By default each **scenario** is one leaf test. Set `scenarioAsTest = false` to make each
**step** its own leaf under a scenario container — useful when you want per-step pass/fail in
the IDE:

```kotlin
class CounterGherkinTest : FreeSpec({
    gherkin("features/counter.feature", counterSteps, scenarioAsTest = false)
})
```

```kotlin
fun <C> FreeSpec.gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    scenarioAsTest: Boolean = true,
)
```

## Tag filtering

Pass a boolean tag expression to run a subset of scenarios:

```kotlin
gherkin("features/checkout.feature", checkoutSteps, tags = "@smoke and not @wip")
```

The KSP-generated test passes no tags by default; with `generateTest = false` you call
`gherkin(...)` yourself and supply the filter (the monorepo wires `-Pbehave.tags="not @wip"`
through to here).

## Hooks on the Steps class

With KSP, implement hook interfaces directly on your `*Steps` class — no builder needed:

```kotlin
@BehaveFeature("features/todo.feature")
class TodoSteps : TodoStepsSpec, ScenarioHooks {
    override suspend fun beforeScenario() { db.clear() }
    override suspend fun afterScenario(info: ScenarioInfo) { log(info.status) }
    // ... step overrides
}
```

```kotlin
interface BeforeScenario { suspend fun beforeScenario() }
interface AfterScenario  { suspend fun afterScenario(info: ScenarioInfo) }
interface ScenarioHooks  : BeforeScenario, AfterScenario   // implement both, default no-ops
```

`beforeScenario()` runs after the scenario-runner setup and before Background steps;
`afterScenario(info)` runs after the last step with the final `ScenarioInfo` (name, tags,
status). For a reusable per-scenario harness across many features, implement `ScenarioRunner`
and delegate (e.g. `ScenarioRunner by ComposeScenarioRunner()`). See the
[Core API hooks](core.md#hooks) for the builder-level equivalents and step-level hooks.
