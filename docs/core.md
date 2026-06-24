# Core API

[← Back to README](../README.md)

`:core` is the runtime — the step-builder DSL, the Gherkin parser, the runner, and the type
registry. It works standalone (no KSP, no Kotest): you register steps by hand and run them
with the built-in `gherkin(...)` runner. Use it when you want the lightest setup or full
control over wiring. Targets: JVM, JS, iOS, macOS, Linux.

- [Dependency](#dependency)
- [Defining steps](#defining-steps)
- [Context](#context)
- [Running](#running)
- [Built-in parameter types](#built-in-parameter-types)
- [Custom parameter types](#custom-parameter-types)
- [DataTables, doc strings, and `Params`](#datatables-doc-strings-and-params)
- [Hooks](#hooks)

---

## Dependency

```kotlin
dependencies {
    testImplementation("io.mcol.kotlin-behave:core:0.1.0")
}
```

## Defining steps

`steps(factory) { ... }` builds a `StepDefinitions<C>`. Inside the block, `Given` / `When` /
`Then` / `And` / `But` register a step expression and a suspend body. Placeholders like
`{int}` and `{string}` are captured positionally and destructured from the lambda argument:

```kotlin
class CounterCtx { var count = 0 }

val counterSteps = steps(::CounterCtx) {
    Given("the counter is {int}") { (n: Int) -> ctx.count = n }
    When("I increment it") { ctx.count++ }
    Then("the counter is {int}") { (n: Int) ->
        assertEquals(n, ctx.count)
    }
}
```

The keywords are interchangeable at runtime (`Given`/`When`/`Then`/`And`/`But` all register
the same way) — pick whichever reads best for the step.

## Context

The `factory` you pass to `steps(...)` produces the **scenario context** `ctx`. A fresh `ctx`
is created for **each scenario**, so state never leaks between scenarios. Access it as `ctx`
inside any step body. Use it to hold the system under test, fixtures, captured values, etc.

## Running

The built-in runner executes a feature file against your step definitions:

```kotlin
suspend fun main() {
    gherkin("features/counter.feature", counterSteps)
}
```

Signatures:

```kotlin
suspend fun <C> gherkin(path: String, steps: StepDefinitions<C>, tags: String? = null)

// Per-scenario wrapper — wrap each scenario in setup/teardown (Compose UI test, DB, etc.)
suspend fun <C> gherkin(
    path: String,
    steps: StepDefinitions<C>,
    tags: String? = null,
    runScenario: suspend (ctx: C, run: suspend () -> Unit) -> Unit,
)
```

- **`tags`** — a filter expression, e.g. `"@smoke and not @wip"`.
- **`runScenario`** — receives the scenario's `ctx` and a `run` lambda; call `run()` where you
  want the steps to execute, around your own setup/teardown.

For IDE test-tree reporting, use the [`:kotest` integration](kotest.md) instead of calling
`gherkin(...)` from `main`.

## Built-in parameter types

| Placeholder | Kotlin type | Matches | Example |
|---|---|---|---|
| `{int}` | `Int` | `-?\d+` | `-42`, `100` |
| `{long}` | `Long` | `-?\d+` | `-42`, `100` |
| `{float}` | `Float` | `-?\d+\.?\d*` | `3.14`, `42` |
| `{double}` | `Double` | `-?\d+\.?\d*` | `3.14`, `42.0` |
| `{string}` | `String` | `"[^"]*"` (quoted) | `"hello world"` |
| `{word}` | `String` | `\S+` | `hello`, `world123` |
| `{boolean}` | `Boolean` | `true\|false` | `true`, `false` |

Unquoted numbers and quoted strings are auto-detected even without an explicit placeholder.

## Custom parameter types

Register a `{placeholder}` → type mapping on the builder. Two overloads — scalar and table:

```kotlin
val steps = steps(::Ctx) {
    // Scalar: {color} matches the pattern, converted from the captured String
    parameterType<Color>("color", "red|green|blue") { Color.valueOf(it.uppercase()) }

    Given("I pick {color}") { (c: Color) -> ctx.color = c }
}
```

```kotlin
// Table/row: convert a DataTable row (column name → cell) into a type
parameterType<Pet>("pet") { row -> Pet(row["name"]!!, row["breed"]!!) }
```

```kotlin
inline fun <reified T> parameterType(name: String, pattern: String, noinline convert: (String) -> T)
inline fun <reified T> parameterType(name: String, noinline convert: (Map<String, String?>) -> T)
```

A custom type registered with the same name as a built-in overrides it. With KSP, prefer the
declarative [`@Type` / `@TypeConverter` / `@BehaveType`](ksp.md#type--typeconverter)
annotations instead of manual registration.

## DataTables, doc strings, and `Params`

Each step body receives a `Params`. Destructure it for positional placeholders, or read the
attached `dataTable` / `docString`:

```kotlin
class Params(
    val dataTable: DataTable? = null,
    val docString: String? = null,
    val docStringContentType: String? = null,
) {
    operator fun <T> get(index: Int): T   // params[0], params[1], ...
}
```

```kotlin
Given("the following pets:") { params ->
    params.dataTable!!.rows.forEach { row -> ctx.pets += Pet(row["name"]!!, row["breed"]!!) }
}

Given("a message:") { params ->
    ctx.message = params.docString!!
}
```

## Hooks

Register lifecycle hooks inside the builder. Scenario, step, and suite scopes are available;
several receive the relevant info object and the context:

```kotlin
val steps = steps(::Ctx) {
    BeforeAll { startServer() }
    AfterAll { stopServer() }

    Before { ctx -> ctx.db = freshDb() }
    Before { info, ctx -> log("→ ${info.name} ${info.tags}") }   // ScenarioInfo
    After { info, ctx -> log("✓ ${info.name} = ${info.status}") }

    BeforeStep { info, ctx -> log("  ${info.keyword} ${info.text}") }   // StepInfo
    AfterStep { ctx -> ctx.screenshotIfFailed() }

    // ... step definitions
}
```

Info objects:

```kotlin
data class ScenarioInfo(val name: String, val tags: Set<String>, val status: ScenarioStatus)
data class StepInfo(val keyword: String, val text: String)
enum class ScenarioStatus { Passed, Failed, Pending, Skipped }
```

With KSP you can also implement the `BeforeScenario` / `AfterScenario` / `ScenarioHooks`
interfaces directly on your `*Steps` class — see [Kotest Integration](kotest.md).
