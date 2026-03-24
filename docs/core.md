# Core API

The `:core` module is the runtime foundation — step builder DSL, Gherkin parser, scenario
runner, type registry, and hooks. Zero dependencies beyond Kotlin stdlib and coroutines.

## Step Builder DSL

Define steps with a context class and the `steps()` builder:

```kotlin
class MyCtx {
    var items = mutableListOf<String>()
}

val mySteps = steps(::MyCtx) {
    Given("I have an empty list") { ctx.items.clear() }
    When("I add {string}") { (s: String) -> ctx.items.add(s) }
    Then("the list contains {int} items") { (n: Int) ->
        assertEquals(n, ctx.items.size)
    }
}
```

### Keywords

All five Gherkin keywords are supported: `Given`, `When`, `Then`, `And`, `But`.
Each takes a step expression and a suspend lambda:

```kotlin
Given("expr") { /* ... */ }
When("expr")  { (param: Type) -> /* ... */ }
Then("expr")  { (p1: Type, p2: Type) -> /* ... */ }
And("expr")   { /* ... */ }
But("expr")   { /* ... */ }
```

### Parameter Destructuring

The lambda receives `Params`, which supports destructuring up to 5 components:

```kotlin
When("I buy {int} items at {double} each") { (count: Int, price: Double) ->
    ctx.total = count * price
}
```

## Built-in Placeholder Types

| Placeholder | Kotlin Type | Regex Pattern | Example match |
|-------------|-------------|---------------|---------------|
| `{int}` | `Int` | `-?\d+` | `42`, `-5` |
| `{long}` | `Long` | `-?\d+` | `9999999999` |
| `{float}` | `Float` | `-?\d+\.?\d*` | `3.14` |
| `{double}` | `Double` | `-?\d+\.?\d*` | `3.14159` |
| `{string}` | `String` | `"[^"]*"` | `"hello"` (quotes stripped) |
| `{word}` | `String` | `\S+` | `active` |
| `{boolean}` | `Boolean` | `true\|false` | `true` |

## Custom Parameter Types

Register custom types to convert matched strings into domain objects:

```kotlin
val steps = steps(::MyCtx) {
    // Scalar type: register name, regex pattern, and converter
    parameterType<Priority>("priority", "high|medium|low") { Priority.valueOf(it.uppercase()) }

    When("I set priority to {priority}") { (p: Priority) ->
        ctx.priority = p
    }
}
```

### DataTable Type Converter

Register a table type converter for DataTable rows:

```kotlin
val steps = steps(::MyCtx) {
    parameterType<User>("user") { row ->
        User(name = row["name"]!!, age = row["age"]!!.toInt())
    }

    Given("the following users:") { params ->
        val users = params.dataTable!!.rows.map { /* already converted */ }
    }
}
```

## Hooks

### Before / After

Hooks run around each scenario. Before hooks run before Background + steps;
After hooks run after all steps in **reverse registration order**.

```kotlin
val steps = steps(::MyCtx) {
    // Context only
    Before { ctx -> ctx.db = createDatabase() }
    After  { ctx -> ctx.db.close() }

    // With ScenarioInfo (name, tags, status)
    Before { info: ScenarioInfo, ctx ->
        println("Starting: ${info.name}")
    }
    After { info: ScenarioInfo, ctx ->
        println("Finished: ${info.name}, status: ${info.status}")
    }

    // Steps...
}
```

### Execution Order

```
1. ScenarioRunner.runScenario() — external harness (if present)
2.   Before hooks (registration order)
3.   Background steps
4.   Scenario steps
5.   After hooks (REVERSE registration order)
```

### Interface-Based Hooks (with KSP)

When using KSP code generation, you can declare hooks as interfaces on the Steps class
instead of registering them in a `steps {}` builder. KSP detects these and generates
the hook registration automatically:

```kotlin
@BehaveFeature("features/todo.feature")
class TodoSteps : TodoStepsSpec, ScenarioHooks {
    override suspend fun beforeScenario() { db.clear() }
    override suspend fun afterScenario(info: ScenarioInfo) { log(info) }
}
```

Available interfaces:

| Interface | Methods |
|-----------|---------|
| `BeforeScenario` | `suspend fun beforeScenario()` |
| `AfterScenario` | `suspend fun afterScenario(info: ScenarioInfo)` |
| `ScenarioHooks` | Both with default no-ops |
| `ScenarioRunner` | `fun runScenario(ctx: Any, run: () -> Unit)` — external test harness |

See [KSP documentation](ksp.md) for full details and examples.

## Pending Steps

Mark a step as not yet implemented:

```kotlin
Given("something not done") { pending("TODO: implement this") }
```

Pending steps cause the scenario to be reported as pending, not failed.

## Step Composition

Merge two `StepDefinitions` with `+`:

```kotlin
val combined = loginSteps + dashboardSteps
```

Duplicate expressions throw `DuplicateStepException` at merge time.

## Tag Filtering

Tag expressions filter which scenarios run:

```kotlin
gherkin("features/app.feature", steps, tags = "@smoke and not @slow")
```

Supported syntax:
- Single tag: `@smoke`
- AND: `@smoke and @critical`
- OR: `@smoke or @regression`
- NOT: `not @wip`
- Parentheses: `(@smoke or @critical) and not @wip`
- Case-insensitive operators

## Running Features

### Suspend runner

```kotlin
suspend fun main() {
    gherkin("features/counter.feature", counterSteps)
}
```

### Non-suspend runner (per-scenario setup)

```kotlin
gherkin("features/app.feature", steps) { ctx, run ->
    // Setup per scenario
    ctx.db = createTestDatabase()
    run()  // executes Before → Background → Steps → After
}
```

### Loading features

```kotlin
val feature = loadFeature("features/counter.feature")
println(feature.name)          // "Counter"
println(feature.scenarios.size) // number of scenarios
```

## DataTable

Steps can have attached data tables:

```gherkin
Given the following users:
  | name  | email           |
  | Alice | alice@test.com  |
  | Bob   | bob@test.com    |
```

Access via `params.dataTable`:

```kotlin
Given("the following users:") { params ->
    val rows = params.dataTable!!.rows  // List<Map<String, String?>>
    rows.forEach { row ->
        println("${row["name"]} - ${row["email"]}")
    }
}
```
