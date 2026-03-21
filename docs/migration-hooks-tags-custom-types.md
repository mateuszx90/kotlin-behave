# Migration Guide — Hooks, Tags, Custom Parameter Types

**Applies to:** kotlin-behave commit `ff750bb` and later

This document covers breaking changes and new APIs introduced in that commit.
Read it before writing or updating step definitions.

---

## 1. Hook signatures changed

The old no-arg `Before { }` / `After { }` overload is **removed**.
There are now two overloads:

| Signature | When to use |
|---|---|
| `Before { ctx -> }` | Access the typed context |
| `Before { info: ScenarioInfo, ctx -> }` | Access scenario metadata + context |

**Before (old):**
```kotlin
Before { setupDatabase() }
After  { teardownDatabase() }
```

**After (new):**
```kotlin
Before { _: Ctx -> setupDatabase() }   // ignore ctx if not needed
After  { _: Ctx -> teardownDatabase() }
```

An empty lambda `Before { }` still compiles — it resolves to `(Ctx) -> Unit` with the
context parameter ignored. But a lambda that captures outer variables like `Before { log.add("x") }`
is now **ambiguous** and must be written explicitly:

```kotlin
// Ambiguous — won't compile:
Before { log.add("before") }

// Fix:
Before { _: Ctx -> log.add("before") }
```

### ScenarioInfo in After hooks

After hooks can now inspect the final scenario status:

```kotlin
After { info: ScenarioInfo, ctx: Ctx ->
    if (info.status == ScenarioStatus.Failed) ctx.takeScreenshot()
}
```

`ScenarioStatus` values: `Passed`, `Failed`, `Pending`, `Skipped`.

---

## 2. Tags

### Parsing

Tags are `@word` tokens on the line immediately above `Feature:`, `Scenario:`,
`Scenario Outline:`, or `Examples:`.

```gherkin
@smoke @regression
Feature: Login

  @happy-path
  Scenario: Successful login
```

Inheritance: feature tags are present on all its scenarios.
`ScenarioInfo.tags` always holds the fully resolved set.

### Filtering at the call site

```kotlin
// suspend variant
gherkin("features/login.feature", loginSteps, tags = "@smoke and not @wip")

// per-scenario variant
gherkin("features/login.feature", loginSteps, tags = "@smoke") { ctx, run -> ... }

// Kotest FreeSpec
gherkin("features/login.feature", loginSteps, tags = "@smoke")
gherkin("features/login.feature", loginSteps, tags = "@smoke") { ctx, run -> ... }
```

Supported syntax: `@tag`, `and`, `or`, `not`, parentheses. Operators are case-insensitive.

Filtered scenarios are **skipped** (not failed). `ScenarioResult.skipped = true`;
`RunResult.hasFailures` is false for skipped scenarios.

---

## 3. Custom parameter types

Register type converters inside the `steps { }` DSL.

### Scalar types — matched inline in step text

```kotlin
enum class Color { RED, GREEN, BLUE }

val mySteps = steps(::MyCtx) {
    parameterType<Color>("color", "[a-z]+") { Color.valueOf(it.uppercase()) }

    Given("the background is {color}") { (c: Color) ->
        ctx.setBackground(c)
    }
}
```

### Table row types — mapped from DataTable

```kotlin
data class Cat(val age: Int, val color: String)

val mySteps = steps(::MyCtx) {
    parameterType<Cat>("cat") { row ->
        Cat(age = row["age"]!!.toInt(), color = row["color"]!!)
    }

    Given("the following cats:") { (cats: List<Cat>) ->
        ctx.addCats(cats)
    }
}
```

The DataTable is automatically mapped row-by-row to `List<Cat>` and passed as `component1()`.

### Merging StepDefinitions

`TypeRegistry` is now **instance-scoped** (not a global singleton).
When combining definitions with `+`, registering the same type name in both throws:

```kotlin
val combined = stepsA + stepsB  // throws if both register the same name
```

---

## 4. Summary of API changes

| Old | New |
|---|---|
| `Before { }` | `Before { _: Ctx -> }` |
| `After { }` | `After { _: Ctx -> }` |
| `GherkinRunner(steps)` | `GherkinRunner(steps, tags = null)` (backward-compatible) |
| `gherkin(path, steps)` | `gherkin(path, steps, tags = null)` (backward-compatible) |
| `TypeRegistry.compile(...)` (global object) | `TypeRegistry().compile(...)` (class instance) |
| No custom types | `parameterType<T>(name, pattern) { }` and `parameterType<T>(name) { row -> }` |
| `ScenarioResult(name, passed, pending, error, failedStep)` | adds `skipped: Boolean` field |
| `RunResult.hasFailures` excludes pending | also excludes skipped |

---

## 5. New modules

| Module | What it is |
|---|---|
| `:annotations` | KMP module — `@BehaveFeature` and `@BehaveType` annotations |
| `:ksp` | JVM KSP processor — reads `.feature` files, generates a `*Spec` interface |

See `docs/superpowers/specs/2026-03-21-ksp-step-definitions-generator-design.md` for the
full KSP design spec. The `:ksp` module is not yet wired into the WortTrainer build.
