# Kotest Integration

The `:kotest` module provides `FreeSpec.gherkin()` extensions that generate a Kotest test tree
from `.feature` files. Each scenario becomes a leaf test in the IDE.

## Setup

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("io.mcol.kotlin-behave:core:0.1.0")
    testImplementation("io.mcol.kotlin-behave:kotest:0.1.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
}

tasks.withType<Test> { useJUnitPlatform() }
```

## Basic Usage

```kotlin
class CounterGherkinTest : FreeSpec({
    gherkin("features/counter.feature", counterSteps)
})
```

Generated test tree:
```
CounterGherkinTest
  Feature: Counter
    Scenario: increment once         ✓
    Scenario: increment twice        ✓
    Scenario: hooks fire per scenario ✓
```

## Per-Scenario Setup

When each scenario needs isolated setup (e.g. database, UI test harness), use the
per-scenario variant:

```kotlin
class TodoGherkinTest : FreeSpec({
    gherkin("features/todo.feature", generatedTodoSteps) { ctx, run ->
        // Runs for EACH scenario — fresh setup every time
        val db = createTestDatabase()
        (ctx as TodoSteps).repository = TodoRepositoryImpl(db)

        run()  // executes: Before hooks → Background → Scenario steps → After hooks

        db.close()
    }
})
```

This is essential for:
- **Compose UI tests** — each scenario needs a fresh `ComposeUiTest`
- **Database tests** — each scenario needs a clean database
- **Integration tests** — each scenario needs isolated dependencies

## Tag Filtering

Filter scenarios by tags:

```kotlin
// Run only @smoke scenarios
class SmokeTest : FreeSpec({
    gherkin("features/app.feature", steps, tags = "@smoke")
})

// Complex expression
class QuickTest : FreeSpec({
    gherkin("features/app.feature", steps, tags = "(@smoke or @critical) and not @wip")
})
```

Feature file:
```gherkin
@smoke
Scenario: Dashboard loads
    Given I am logged in
    Then I see the dashboard

@slow
Scenario: Full data load
    Given I am logged in
    Then all records are displayed
```

## Step-Level Reporting

By default, each scenario is a single leaf test. For step-level reporting in the IDE,
set `scenarioAsTest = false`:

```kotlin
class DetailedTest : FreeSpec({
    gherkin("features/app.feature", steps, scenarioAsTest = false)
})
```

Generated tree with `scenarioAsTest = false`:
```
DetailedTest
  Feature: App
    Scenario: Login
      Given I am on the login page        ✓
      When I enter valid credentials       ✓
      Then I see the dashboard             ✓
```

With step-level reporting:
- `beforeContainer` / `afterContainer` dispatch hooks at the `Scenario:` container level
- Each step is a separate leaf test
- Failures show exactly which step failed

## Multiple Features in One Test

```kotlin
class FullSuiteTest : FreeSpec({
    gherkin("features/login.feature", loginSteps)
    gherkin("features/dashboard.feature", dashboardSteps)
    gherkin("features/settings.feature", settingsSteps)
})
```

## Combining Step Definitions

Merge steps from multiple sources:

```kotlin
val allSteps = loginSteps + dashboardSteps

class AppTest : FreeSpec({
    gherkin("features/app.feature", allSteps)
})
```

## Pending Scenarios

Steps that call `pending()` mark the scenario as pending (not failed):

```kotlin
Given("something not implemented") { pending("TODO") }
```

Output:
```
  [PENDING] Scenario: Something not implemented
```
