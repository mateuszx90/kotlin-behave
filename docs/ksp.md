# KSP Code Generation

The `:ksp` module is a KSP annotation processor that reads `.feature` files at build time
and generates type-safe `*StepsSpec` interfaces. The compiler tells you if a step is missing —
no string matching surprises at runtime.

## Setup

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.1.20-1.0.32"
}

dependencies {
    compileOnly("io.mcol.kotlin-behave:annotations:0.1.0")
    kspTest("io.mcol.kotlin-behave:ksp:0.1.0")
    testImplementation("io.mcol.kotlin-behave:core:0.1.0")
    testImplementation("io.mcol.kotlin-behave:kotest:0.1.0")
}

ksp {
    arg("behave.featureDir", "src/test/resources")      // default: src/commonTest/resources
    arg("behave.projectDir", projectDir.absolutePath)
}
```

### KMP Projects

For Kotlin Multiplatform, use target-specific KSP configurations:

```kotlin
dependencies {
    add("kspDesktopTest", project(":ksp"))
    add("kspJvmTest", project(":ksp"))
}
```

## How It Works

```
features/todo.feature         ← you write this
    ↓ KSP reads at build time and generates:
TodoStepsSpec.kt              ← interface + val + test class (don't edit)
    ↓ you implement
TodoSteps.kt                  ← @BehaveFeature class (only file you write)
    ↓ run
./gradlew test                ← generated test class runs automatically
```

## @BehaveFeature

Marks a class as a step definitions implementation for a feature file:

```kotlin
@BehaveFeature("features/todo.feature")
class TodoSteps : TodoStepsSpec {
    // implement generated methods
}
```

The `path` is relative to the `behave.featureDir` KSP option.

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `path` | (required) | Path to `.feature` file, relative to `behave.featureDir` |
| `generateTest` | `true` | When true, KSP also generates a `val` and a Kotest test class |

## Generated Output

By default, KSP generates three things from a single `@BehaveFeature` annotation:

| Generated | What it is |
|-----------|------------|
| `TodoStepsSpec` | Interface with one `suspend fun` per unique step |
| `val generatedTodoSteps` | `StepDefinitions` instance wired to your class |
| `class TodoGherkinTest` | Kotest `FreeSpec` that runs all scenarios |

The user only writes the `@BehaveFeature` class. Everything else is generated:

```kotlin
// You write ONLY this:
@BehaveFeature("features/todo.feature")
class TodoSteps : TodoStepsSpec {
    override suspend fun givenTheTodoListIsEmpty() { /* ... */ }
    override suspend fun whenIAddATodo(string: String) { /* ... */ }
    override suspend fun thenTheTodoIsDisplayed(string: String) { /* ... */ }
}

// KSP generates: TodoStepsSpec interface, generatedTodoSteps val, TodoGherkinTest class
// Run: ./gradlew test
```

### Lifecycle Interfaces

Implement these interfaces on your Steps class — KSP detects them and generates
the appropriate hook registration and test class wiring automatically.

#### BeforeScenario / AfterScenario

```kotlin
@BehaveFeature("features/todo.feature")
class TodoSteps : TodoStepsSpec, BeforeScenario {
    override suspend fun beforeScenario() {
        db.clear()
    }
    // ... step overrides
}
```

```kotlin
@BehaveFeature("features/todo.feature")
class TodoSteps : TodoStepsSpec, AfterScenario {
    override suspend fun afterScenario(info: ScenarioInfo) {
        println("${info.name}: ${info.status}")
    }
}
```

#### ScenarioHooks (combines both with default no-ops)

```kotlin
@BehaveFeature("features/todo.feature")
class TodoSteps : TodoStepsSpec, ScenarioHooks {
    override suspend fun beforeScenario() { db.clear() }
    // afterScenario has default no-op — override only what you need
}
```

KSP generates:
```kotlin
val generatedTodoSteps = TodoStepsSpec.steps { TodoSteps() }.also { defs ->
    defs.stepBuilder.Before { ctx -> (ctx as BeforeScenario).beforeScenario() }
    defs.stepBuilder.After { info, ctx -> (ctx as AfterScenario).afterScenario(info) }
}
```

#### ScenarioRunner (external test harness)

When each scenario needs an external test environment (Compose UI, database, etc.),
implement `ScenarioRunner`. Use Kotlin delegation (`by`) to share across tests:

```kotlin
class ComposeScenarioRunner : ScenarioRunner {
    override fun runScenario(ctx: Any, run: () -> Unit) {
        runComposeUiTest {
            (ctx as HasAppRobot).app = AppRobotImpl(this)
            run()  // executes Before hooks → Background → Steps → After hooks
        }
    }
}

@BehaveFeature("features/collections.feature")
class CollectionsSteps : CollectionsStepsSpec, HasAppRobot,
    ScenarioRunner by ComposeScenarioRunner() {
    override lateinit var app: AppRobot
}
```

KSP generates:
```kotlin
class CollectionsGherkinTest : FreeSpec({
    gherkin("features/collections.feature", generatedCollectionsSteps) { ctx, run ->
        (ctx as ScenarioRunner).runScenario(ctx, run)
    }
})
```

#### Combining all interfaces

```kotlin
@BehaveFeature("features/collections.feature")
class CollectionsSteps : CollectionsStepsSpec, HasAppRobot,
    ScenarioRunner by ComposeScenarioRunner(),
    ScenarioHooks {
    override lateinit var app: AppRobot

    override suspend fun beforeScenario() { /* after ComposeUiTest setup, before Background */ }
    override suspend fun afterScenario(info: ScenarioInfo) { /* after all steps */ }
}
```

| Interface | Purpose | Runs |
|-----------|---------|------|
| `ScenarioRunner` | External test harness (Compose, DB) | Wraps entire lifecycle |
| `BeforeScenario` | Per-scenario setup | After runner setup, before Background |
| `AfterScenario` | Per-scenario cleanup/logging | After all steps, receives status |
| `ScenarioHooks` | Both hooks with default no-ops | Combines both above |

### Opting out of test generation

Set `generateTest = false` when you need fully manual wiring — custom parameter types
or tag filtering:

```kotlin
@BehaveFeature("features/todo.feature", generateTest = false)
class TodoSteps : TodoStepsSpec { /* ... */ }

// Manual wiring with custom types
val todoSteps = TodoStepsSpec.steps { TodoSteps() }.also { defs ->
    defs.stepBuilder.parameterType<Priority>("priority", "\\S+") { Priority.valueOf(it) }
}

// Manual test class with tag filtering
class TodoSmokeTest : FreeSpec({
    gherkin("features/todo.feature", todoSteps, tags = "@smoke")
})
```

## Generated Interface Details

Given this feature file:

```gherkin
Feature: Todo list

  Background:
    Given the todo list is empty

  Scenario: Add a todo
    When I add a todo "Buy groceries"
    Then the todo "Buy groceries" is displayed

  Scenario: Import todos
    When I import the following todos:
      | title       | priority |
      | Clean house | high     |
      | Read book   | low      |
    Then 2 todos are displayed
```

KSP generates:

```kotlin
// Generated by kotlin-behave KSP — do not edit

data class WhenIImportTheFollowingTodosRow(val title: String, val priority: String)

interface TodoStepsSpec {

    suspend fun givenTheTodoListIsEmpty()
    suspend fun whenIAddATodo(string: String)
    suspend fun thenTheTodoIsDisplayed(string: String)
    suspend fun whenIImportTheFollowingTodos(rows: List<WhenIImportTheFollowingTodosRow>)
    suspend fun thenTodosAreDisplayed(int: Int)

    companion object {
        fun steps(factory: () -> TodoStepsSpec): StepDefinitions<TodoStepsSpec> =
            steps(factory) {
                Given("the todo list is empty") { ctx.givenTheTodoListIsEmpty() }
                When("I add a todo {string}") { (p1: String) ->
                    ctx.whenIAddATodo(p1)
                }
                // ... all steps registered with correct expressions
            }
    }
}

val generatedTodoSteps = TodoStepsSpec.steps { TodoSteps() }

class TodoGherkinTest : FreeSpec({
    gherkin("features/todo.feature", generatedTodoSteps)
})
```

## Auto-Detected Parameter Types

KSP auto-detects parameter types from concrete values in step text:

| Value in feature | Detected as | Kotlin type | Method param |
|------------------|-------------|-------------|--------------|
| `"quoted text"` | `{string}` | `String` | `string` |
| `42` (integer) | `{int}` | `Int` | `int` |
| `3.14` (decimal) | `{double}` | `Double` | `double` |
| `<variable>` (outline) | `{word}` | `String` | variable name |

Multiple params of the same type get indexed: `string0`, `string1`.

## Method Name Generation

| Step text | Generated method |
|-----------|------------------|
| `Given I am on the login page` | `givenIAmOnTheLoginPage()` |
| `When I enter "admin"` | `whenIEnter(string)` |
| `Then I have 5 items` | `thenIHaveItems(int)` |
| `Given the following cats:` | `givenTheFollowingCats(rows)` |
| `When I type "<answer>"` | `whenIType(answer)` |

Collisions get numeric suffixes: `thenIHaveItems0`, `thenIHaveItems1`.

## Scenario Outline

Outline `<variable>` tokens are preserved at compile time. At runtime, each Examples row
expands into a separate scenario.

```gherkin
Scenario Outline: Login as <role>
    Given a user "<username>" with role "<role>"
    When I login with password "<password>"

    Examples:
      | username | role    | password |
      | alice    | admin   | pass123  |
      | bob      | viewer  | secret   |
```

- Quoted `"<variable>"` → `{string}` (matched with quotes at runtime)
- Unquoted `<variable>` → `{word}` (matched as non-whitespace)
- Parameter named from the variable name, not generic `string`/`word`

## DataTable

Steps followed by a `| table |` get a Row data class generated:

```gherkin
Given the following users:
  | name  | email           | age |
  | Alice | alice@test.com  | 30  |
```

Generated:
```kotlin
data class GivenTheFollowingUsersRow(val name: String, val email: String, val age: String)

suspend fun givenTheFollowingUsers(rows: List<GivenTheFollowingUsersRow>)
```

All columns default to `String`. Use `@BehaveType` to override.

### DataTable + Inline Parameters

A step can have both inline parameters and a DataTable:

```gherkin
When I assign the following words to "Animals":
  | polish | english |
  | pies   | dog     |
```

Generated:
```kotlin
suspend fun whenIAssignTheFollowingWordsTo(string: String, rows: List<...>)
```

## @BehaveType — Custom Type Mapping

### Placeholder mode

Map a single `{placeholder}` token to a domain type:

```kotlin
@BehaveFeature("features/navigation.feature")
@BehaveType(placeholder = "label", type = ButtonLabel::class)
class NavigationSteps : NavigationStepsSpec {
    override suspend fun whenITapTheButton(label: ButtonLabel) { /* ... */ }
}
```

Step `When I tap the {label} button` → method receives `ButtonLabel`.

### Field auto-detect mode

Group multiple placeholders into one type by matching constructor parameter names:

```kotlin
data class Credentials(val username: String, val password: String)

@BehaveFeature("features/auth.feature")
@BehaveType(type = Credentials::class)  // absorbs {username} + {password}
class AuthSteps : AuthStepsSpec {
    override suspend fun whenILoginWithAndHavingMaxAttempts(credentials: Credentials, int: Int) { /* ... */ }
}
```

### Field explicit mode

Explicitly name which placeholders to group:

```kotlin
@BehaveType(type = OrderItem::class, fields = ["product", "size"])
```

### DataTable type mapping

`@BehaveType` works the same for DataTable columns. When the type covers **all** columns,
no Row class is generated — the method takes `List<YourType>` directly:

```kotlin
data class WordRow(val polish: String, val english: String)

@BehaveFeature("features/learning.feature")
@BehaveType(type = WordRow::class)
class LearningSteps : LearningStepsSpec {
    override suspend fun givenTheFollowingVocabulary(rows: List<WordRow>) { /* ... */ }
}
```

> **Convention:** Name your type with a `Row` suffix (e.g. `WordRow`) for KSP to generate
> inline mapping code. Without the suffix, KSP generates a raw cast.

Combine multiple `@BehaveType` annotations for partial mappings:

```kotlin
@BehaveType(type = Pet::class)                         // absorbs name + breed columns
@BehaveType(placeholder = "age", type = PetAge::class)  // maps age column
```

## @BehaveCast — Lossy Conversion

When a step auto-detects as `{int}` but some concrete values are decimals:

```gherkin
Scenario: Whole portions
    When I create a recipe with 4 portions

Scenario: Decimal portions
    When I create a recipe with 2.5 portions
```

Without `@BehaveCast`, KSP reports a compile error. With it, the expression is widened
and conversion code is generated:

```kotlin
override suspend fun whenICreateARecipeWithPortions(@BehaveCast int: Int) {
    // KSP widens {int} → {double} in the step expression
    // Generated code: p1.toInt() (truncates 2.5 → 2)
}
```

Widening map:
- `Int` → receives as `Double`, converts via `.toInt()`
- `Long` → receives as `Double`, converts via `.toLong()`
- `Float` → receives as `Double`, converts via `.toFloat()`

## Compile-Time Validation

KSP validates concrete values against declared placeholder types:

| Situation | Outcome |
|-----------|---------|
| Feature file not found | **Error** with full resolved path |
| Value `"abc"` used where `{int}` expected | **Error**: type mismatch |
| `@BehaveType` auto-detect with zero constructor params | **Error** |
| Two `@BehaveType` entries claim same placeholder | **Error** |
| Two steps normalise to same method name | Numeric suffix added |

## KSP Options

| Option | Default | Description |
|--------|---------|-------------|
| `behave.featureDir` | `src/commonTest/resources` | Root for `.feature` files |
| `behave.projectDir` | `.` | Absolute path to project directory |
