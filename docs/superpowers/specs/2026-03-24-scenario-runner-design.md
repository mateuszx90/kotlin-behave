# ScenarioRunner Interface — Design Spec

**Date:** 2026-03-24
**Status:** Approved

## Problem

KSP generates `val generatedXxxSteps` and `XxxGherkinTest` automatically, eliminating
boilerplate. But UI tests (Compose, etc.) need per-scenario setup: injecting test harness,
launching app content, setting timeouts. This forces `generateTest = false` and manual
boilerplate — a separate `GherkinTest` file per feature with copy-pasted setup code.

## Solution

Add a `ScenarioRunner` interface to `:core`. When a Steps class implements it, KSP detects
it and generates the test class with the per-scenario runner lambda. Users share setup logic
via Kotlin's delegation pattern (`by`).

## Design

### 1. `ScenarioRunner` interface (`:core`)

```kotlin
// core/src/commonMain/kotlin/io/mcol/behave/steps/ScenarioRunner.kt
interface ScenarioRunner {
    fun runScenario(ctx: Any, run: () -> Unit)
}
```

- Lives in `io.mcol.behave.steps` alongside `StepBuilder`, `StepDefinitions`
- `ctx: Any` — the Steps instance (passed by the generated test class)
- `run: () -> Unit` — executes Before hooks → Background → Scenario steps → After hooks
- Non-suspend: bridges to suspend internally (same as existing `gherkin(runScenario)`)

### 2. KSP detection and code generation

In `BehaveProcessor.processClass()`:
- Check if the annotated class implements `ScenarioRunner` (via `classDecl.superTypes`)
- Pass `hasScenarioRunner: Boolean` to `GeneratedInterface`

In `CodeGenerator.render()`:
- When `hasScenarioRunner = true` AND `generateTest = true`:

```kotlin
val generatedXxxSteps = XxxStepsSpec.steps { XxxSteps() }

class XxxGherkinTest : FreeSpec({
    gherkin("features/xxx.feature", generatedXxxSteps) { ctx, run ->
        (ctx as ScenarioRunner).runScenario(ctx, run)
    }
})
```

- When `hasScenarioRunner = false` AND `generateTest = true` (existing behavior):

```kotlin
val generatedXxxSteps = XxxStepsSpec.steps { XxxSteps() }

class XxxGherkinTest : FreeSpec({
    gherkin("features/xxx.feature", generatedXxxSteps)
})
```

- When `generateTest = false`: no val or test class generated (existing behavior)

### 3. Import added to generated file

Add `import io.mcol.behave.steps.ScenarioRunner` to the generated spec file when
`hasScenarioRunner = true`.

### 4. User-side pattern (WortTrainer example)

Shared infrastructure (defined once per app):

```kotlin
interface HasAppRobot {
    var app: AppRobot
}

class ComposeScenarioRunner(
    private val timeout: Long = 10_000,
) : ScenarioRunner {
    override fun runScenario(ctx: Any, run: () -> Unit) {
        runTestWithEnglishLocale { _ ->
            (ctx as HasAppRobot).app = AppRobotImpl(this)
            setAppContent()
            withScenarioTimeout(timeout) { run() }
        }
    }
}
```

Steps class — one line of delegation:

```kotlin
@BehaveFeature("features/collections_screen.feature")
class CollectionsSteps : CollectionsStepsSpec, HasAppRobot,
    ScenarioRunner by ComposeScenarioRunner() {
    override lateinit var app: AppRobot
    // ... step overrides only
}
```

Custom setup — override delegation:

```kotlin
@BehaveFeature("features/learning_screen.feature")
class LearningSteps : LearningStepsSpec, HasAppRobot,
    ScenarioRunner by ComposeScenarioRunner() {
    override lateinit var app: AppRobot

    override fun runScenario(ctx: Any, run: () -> Unit) {
        runTestWithEnglishLocale { _ ->
            // Extra: Koin module override
            GlobalContext.get().loadModules(
                listOf(module { single<WordShuffler> { WordShuffler { w -> w } } }),
                allowOverride = true
            )
            (ctx as HasAppRobot).app = AppRobotImpl(this)
            setAppContent()
            withScenarioTimeout(10_000) { run() }
        }
    }
}
```

## Files to change

| File | Change |
|------|--------|
| `core/.../steps/ScenarioRunner.kt` | New file: `ScenarioRunner` interface |
| `ksp/.../BehaveProcessor.kt` | Detect `ScenarioRunner` supertype, pass to `GeneratedInterface` |
| `ksp/.../CodeGenerator.kt` | Add `hasScenarioRunner` field, conditional test class generation |
| `ksp/.../CodeGeneratorTest.kt` | Tests for both variants of generated test class |

## Not in scope

- `HasAppRobot` and `ComposeScenarioRunner` are user-side — they live in the app's test
  utils, not in kotlin-behave
- No changes to the runtime `GherkinRunner` or `gherkin()` functions
- No changes to `@BehaveFeature` annotation (uses existing `generateTest` parameter)
