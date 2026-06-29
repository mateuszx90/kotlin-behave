# `:core` — runtime engine

`:core` is the multiplatform runtime that executes a parsed `.feature` against your step
definitions. It owns three things: **parsing** Gherkin into a model, **matching** each step
line to a step definition, and **converting** the captured text into typed arguments.

> Compile-time validation lives in `:ksp` (the KSP processor). Many mistakes are caught there
> before the runtime ever runs — see [Compile-time vs runtime](#compile-time-vs-runtime). This
> document describes what the runtime itself does.

---

## How a feature is parsed

Two parsers exist, by design:

| Parser | Module | Used by | Expands `Examples`? |
|--------|--------|---------|---------------------|
| `GherkinParser` | `:core` (`parser/GherkinParser.kt`) | the **runtime** | yes — one concrete scenario per row |
| `FeatureFileParser` | `:gherkin` | the **KSP processor** (and IntelliJ plugin) | no — keeps `<variable>` tokens so it can generate one step binding |

Both read the same grammar: `Feature:`, `Background:`, `Scenario:` / `Example:`,
`Scenario Outline:` / `Scenario Template:` with an `Examples:` / `Scenarios:` table, `And` / `But`
/ `*` continuations (resolved to the preceding `Given`/`When`/`Then`), `| data | tables |`, and
`"""` / ` ``` ` doc strings.

A **Scenario Outline** is a template: each `<variable>` in a step is substituted from the
`Examples` table, producing one concrete scenario per data row.

---

## Step matching & the placeholder type system

A step definition is registered with an expression such as `I have {int} items`. `TypeRegistry`
compiles that into an anchored regex (`^I have (-?\d+) items$`) plus an ordered list of
converters, then matches it against the real step line. Converters run **left-to-right by their
position in the text**, not by definition order.

### Built-in placeholders

| Placeholder | Value regex | Converted with | Kotlin type |
|-------------|-------------|----------------|-------------|
| `{int}`     | `-?\d+`        | `toInt()`            | `Int`     |
| `{long}`    | `-?\d+`        | `toLong()`           | `Long`    |
| `{float}`   | `-?\d+\.?\d*`  | `toFloat()`          | `Float`   |
| `{double}`  | `-?\d+\.?\d*`  | `toDouble()`         | `Double`  |
| `{boolean}` | `true\|false`  | `toBooleanStrict()`  | `Boolean` |
| `{word}`    | `\S+`          | (raw)                | `String`  |
| `{string}`  | `"[^"]*"`      | strips the quotes    | `String`  |

Notes:
- `{float}`/`{double}` accept a **bare integer** (`5`) and a **trailing dot** (`5.`) — the regex is
  `-?\d+\.?\d*`, not `-?\d+\.\d+`.
- The regex only decides whether a step *matches*. The converter can still fail at runtime:
  `{int}` matches `9999999999` but `toInt()` overflows. (The KSP processor catches that at build
  time — see below.)
- A value that doesn't match its placeholder regex means the step line simply doesn't match that
  definition (reported as an undefined/ambiguous step), it is not coerced.

### Single source of truth for the patterns

The scalar value regexes (`int`, `long`, `float`, `double`, `boolean`, `word`) live in
`GherkinTypes.builtinValuePatterns` in `:gherkin`. **Both** the runtime matcher (`TypeRegistry`)
and the compile-time validator (`TypeValidator`) read them from there, so the two can never drift.
`{string}` is intentionally per-side: the runtime matches the quoted token `"…"`, while the
validator sees the value with quotes already stripped.

### Custom types

- **Scalar:** `register(name, pattern, convert)` adds a `{name}` placeholder. Custom names
  override built-ins of the same name.
- **Table:** `registerTableType(name, convert)` maps a `| data table |` to a domain object.

In step definitions you usually reach these through annotations (resolved by the KSP processor):

- `@Type(SomeEnum::class)` — converts case-insensitively via `valueOf` (no converter needed).
- `@Type(Duration::class)` — parsed by the built-in `kotlin.time.Duration.parse`.
- `@TypeConverter fun(…): T` — a hand-written mapping; it may consume **multiple** captured tokens
  (e.g. `width x height` → one `Size`).

---

## Compile-time vs runtime

The same value rules are applied in both places, but the **timing and the failure mode** differ.
The KSP processor predicts, at build time, failures the runtime would otherwise hit:

| Concern | Runtime behaviour | Caught at compile time by `:ksp`? |
|---------|-------------------|-----------------------------------|
| Value shape (`{int}`, `{double}`, …) | regex match (shared patterns) | yes — same patterns, so they agree |
| Numeric overflow (`toInt`/`toLong`) | throws while converting | yes — range-checked |
| Enum literal not a constant | `ValueValidation.toEnum` throws the shared message | yes — same rule (`ValueValidation.enumProblem`) |
| `Duration` literal unparseable | `ValueValidation.toDuration` throws the shared message | yes — same rule (`ValueValidation.durationProblem`) |
| `@TypeConverter` arity mismatch | `IndexOutOfBounds` while converting | yes |
| Inconsistent `DataTable` usage | `params.dataTable!!` NPE | yes |
| Scenario Outline `<var>` with no column | substitutes a literal `<var>` | yes (parser error) |
| Missing `Examples`, missing `Feature:`, orphaned step | parse error | yes (parser error) |

Because the placeholder patterns are shared, a value the runtime can match is exactly a value the
validator accepts — there is no class of input that passes one and fails the other on shape.

The enum / `Duration` / numeric-range **rules** are shared too: `io.mcol.behave.types.ValueValidation`
exposes a build-time predicate (used by the KSP processor) and a runtime conversion (called by the
generated code) backed by the *same* logic and wording. So when a value escapes compile-time — a
`.feature` edited after the build, or a dynamically-registered type — the runtime fails with the same
friendly message the build would have produced, not a raw `valueOf` / `parse` exception.
