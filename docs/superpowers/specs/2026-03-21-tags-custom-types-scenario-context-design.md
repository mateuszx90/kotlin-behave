# Design: Tags, Custom Parameter Types & Scenario Context in Hooks

**Date:** 2026-03-21
**Scope:** kotlin-behave core library
**Status:** Approved

---

## Overview

Three related features that bring kotlin-behave closer to the full Gherkin/Cucumber spec:

1. **Tags** — `@tag` annotations on features/scenarios with boolean filter expressions
2. **Scenario context in hooks** — `Before`/`After` hooks receive scenario metadata (name, tags, status) alongside the typed test context
3. **Custom parameter types** — user-defined type converters registered in the `steps {}` DSL, including DataTable-row-to-object mapping

Delivered in two independent tracks:
- **Track A:** Tags + Scenario context in hooks (tightly coupled)
- **Track B:** Custom parameter types (fully independent)

---

## Track A: Tags + Scenario Context in Hooks

### Tag Parsing

Tags are `@word` tokens on the line immediately above `Feature:`, `Scenario:`, `Scenario Outline:`, or `Examples:`. Multiple tags on one line, space-separated.

```gherkin
@smoke @auth
Feature: Login

  @happy-path
  Scenario: Successful login

  @wip @ignore
  Scenario: Forgot password

  @slow
  Scenario Outline: Login with <role>
    ...
    @admin
    Examples:
      | role  |
      | admin |
```

**Inheritance rules:**
- Tags on `Feature:` are inherited by all its scenarios
- Tags on `Examples:` are merged with tags of the parent `Scenario Outline`
- Final scenario tags = own tags ∪ feature tags (∪ examples tags if outline)
- Tags for expanded outline rows are fully resolved by the parser before the runner sees them — `ScenarioInfo.tags` always holds the final merged set

**Tag format:** Stored as strings including the `@` prefix (e.g. `"@smoke"`). The filter expression parser also expects `@` prefix tokens. Comparison is character-for-character: `"@smoke" in scenario.tags` works as written in feature files.

### Model Changes

```kotlin
data class Feature(
    val name: String,
    val scenarios: List<Scenario>,
    val background: Background? = null,
    val tags: Set<String> = emptySet()
)

data class Scenario(
    val name: String,
    val steps: List<Step>,
    val tags: Set<String> = emptySet()
)
```

### Tag Filtering

**Expression syntax** (Cucumber-compatible boolean expressions):

```
@smoke and not @wip
(@smoke or @auth) and not @slow
@smoke                              // single tag
not @ignore                         // negation
```

Supported operators: `and`, `or`, `not`, parentheses. Case-insensitive operators.

**Resolution order:**
1. `tags` parameter at the `gherkin()` call site
2. Platform-provided system property `behave.tags` (via expect/actual — on JVM: `System.getProperty("behave.tags")`, on JS/native: not supported, returns null)
3. No filter — run all scenarios

```kotlin
// both overloads gain a tags parameter
suspend fun gherkin(path: String, steps: StepDefinitions<*>, tags: String? = null)
fun gherkin(path: String, steps: StepDefinitions<*>, tags: String? = null, runScenario: ...)
```

**Filtered scenarios** are reported as skipped (not failed, not silently dropped).

### Scenario Context in Hooks

**New model:**

```kotlin
enum class ScenarioStatus { Passed, Failed, Pending, Skipped }

data class ScenarioInfo(
    val name: String,
    val tags: Set<String>,
    val status: ScenarioStatus  // Passed in Before hooks; meaningful in After
)
```

`ScenarioResult` gains a `skipped: Boolean` field to track filtered scenarios in run reports.

**Hook storage — sealed type for mixed signatures:**

`StepBuilder` stores hooks as a sealed type to support all three overloads cleanly:

```kotlin
sealed interface Hook<Ctx> {
    class NoArg<Ctx>(val block: suspend () -> Unit) : Hook<Ctx>
    class WithCtx<Ctx>(val block: suspend (Ctx) -> Unit) : Hook<Ctx>
    class WithScenarioAndCtx<Ctx>(val block: suspend (ScenarioInfo, Ctx) -> Unit) : Hook<Ctx>
}
```

`StepBuilder` holds `val beforeHooks: MutableList<Hook<Ctx>>` and `val afterHooks: MutableList<Hook<Ctx>>`.

The runner dispatches each hook by type:

```kotlin
when (hook) {
    is Hook.NoArg -> hook.block()
    is Hook.WithCtx -> hook.block(ctx)
    is Hook.WithScenarioAndCtx -> hook.block(scenarioInfo, ctx)
}
```

**All three overloads are NEW** (the current codebase has `suspend () -> Unit` only). All are added in this spec:

```kotlin
// DSL registration
Before { }                                    // Hook.NoArg
Before { ctx -> }                             // Hook.WithCtx
Before { scenario: ScenarioInfo, ctx -> }     // Hook.WithScenarioAndCtx

After { }
After { ctx -> }
After { scenario: ScenarioInfo, ctx -> }
```

Existing code using `Before { }` continues to compile unchanged.

**Tag-targeted hooks example:**

```kotlin
Before { scenario, ctx ->
    if ("@db" in scenario.tags) setupDatabase(ctx)
}

After { scenario, ctx ->
    if (scenario.status == ScenarioStatus.Failed) captureScreenshot(ctx)
}
```

---

## Track B: Custom Parameter Types

### DataTableRow type

```kotlin
typealias DataTableRow = Map<String, String?>
```

Values are `String?` — `null` when the cell text is `"null"` (consistent with existing DataTable null-cell handling).

### Registration

Custom types are registered inside the `steps { }` DSL alongside the steps that use them.
`TypeRegistry` becomes **instance-scoped** (moved from a global `object` to a field on `StepDefinitions`):

```kotlin
val mySteps = steps(::MyCtx) {
    // scalar — matched inline in step text via {color}
    parameterType<Color>("color", "[a-z]+") { value: String ->
        Color.valueOf(value.uppercase())
    }

    // table row — maps one DataTable row to an object
    parameterType<Cat>("cat") { row: DataTableRow ->
        Cat(age = row["age"]!!.toInt(), color = row["color"]!!)
    }

    Given("the background is {color}") { (c: Color) -> ... }
    Given("the following cats:") { (cats: List<Cat>) -> ... }
}
```

**Two overloads of `parameterType`:**

| Overload | When to use |
|---|---|
| `parameterType<T>(name, regex) { value: String -> T }` | Scalar — matched inline in step text |
| `parameterType<T>(name) { row: DataTableRow -> T }` | Table row — maps one DataTable row to T |

### Type Merging

When `StepDefinitions` are combined with `+`, their instance-local `TypeRegistry` instances are merged:

```kotlin
val combined = stepsA + stepsB
```

**Conflict rule:** same type name = conflict, regardless of pattern or converter. Two definitions registering `"color"` throw at merge time, even if the patterns happen to be identical. This avoids non-comparable lambda equality checks and gives a clear, consistent rule.

### Type Resolution Order

1. Custom types from the current `StepDefinitions` (and any merged definitions)
2. Built-in types (`{int}`, `{long}`, `{float}`, `{double}`, `{string}`, `{word}`)

A custom type with the same name as a built-in overrides the built-in.

### DataTable List Mapping

When a step parameter is `List<T>` and `T` has a registered table row converter, the DataTable attached to the step is automatically mapped row-by-row:

```gherkin
Given the following cats:
  | age | color |
  | 3   | black |
  | 5   | white |
```

```kotlin
Given("the following cats:") { (cats: List<Cat>) ->
    // cats = [Cat(age=3, color="black"), Cat(age=5, color="white")]
}
```

### Error Handling

- Unknown type name in step expression → clear error at test startup pointing to the step pattern
- Conversion failure → clear error at runtime pointing to step name + type name + raw value
- Null cell value passed to non-null converter → clear error with cell coordinates

---

## Testing Strategy

Each feature ships with unit tests in `core/src/commonTest/`:

**Tags:**
- Parser: tags at all positions (feature, scenario, outline, examples), inheritance, multi-tag lines
- Filter: `and`, `or`, `not`, parentheses, case-insensitive operators, no-filter runs all
- Runner: skipped scenarios reported correctly; system property fallback (JVM only)
- Both `suspend gherkin()` and `runWithPerScenarioRunner` paths covered

**Scenario context:**
- All three hook overloads compile and receive correct values
- `After` hook sees `Failed` status on step failure, `Passed` on success, `Pending` on pending step
- `After` hook sees `Skipped` for filtered scenarios
- Existing no-arg `Before { }` hooks still work

**Custom types:**
- Scalar type registration, resolution in step expressions
- Table row mapping, `List<T>` auto-mapping
- Merge conflict detection (same name throws)
- TypeRegistry is instance-scoped — two `StepDefinitions` with same name don't interfere
- Error messages for unknown type, conversion failure, null cell

---

## Future Extensions (Out of Scope)

### KSP Annotation Processor — `@BehaveTable`

A `:processor` module using KSP to generate `parameterType` registrations for annotated data classes:

```kotlin
@BehaveTable
data class Cat(val age: Int, val color: String)
```

Generates `DataTableRow -> Cat` converter at compile time. Registration via `install(GeneratedBehaveMappings)` in the `steps {}` DSL. Separate spec, plan, and implementation cycle.

### KSP Step Definitions Generator — `@BehaveFeature`

A KSP processor that reads a `.feature` file and generates a Kotlin interface with one method per step:

```kotlin
@BehaveFeature("features/login.feature")
interface LoginSteps {
    // generated:
    suspend fun givenIAmOnTheLoginPage()
    suspend fun whenIEnterUsername(username: String)
    suspend fun thenISeeTheDashboard()
}
```

Implementing the interface + passing it to `gherkin()` would auto-wire all step definitions. Eliminates string matching entirely. Separate spec, plan, and implementation cycle.

### Other

- Doc Strings (`"""..."""`)
- i18n keywords
- BeforeStep/AfterStep hooks
- Native resource loading
- JSON/HTML report formats
- Dry run / strict mode
