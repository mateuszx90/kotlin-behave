# kotlin-behave KSP — Examples

Runnable examples demonstrating every use case of the kotlin-behave KSP processor.
Each example is a `.feature` file + Kotlin step definitions + Kotest test runner.

## Run

```bash
./gradlew :examples:test
```

## Examples

| # | Package | Feature | Use cases |
|---|---------|---------|-----------|
| 1 | `ex01_basic` | Basic steps | Literal steps, `Background`, `Given`/`When`/`Then`/`And`/`But` |
| 2 | `ex02_strings` | String parameters | Quoted `"literal"` auto-detection, indexed `string0`/`string1` |
| 3 | `ex03_numbers` | Number parameters | Auto-detected `Int` and `Double` from inline numbers |
| 4 | `ex04_placeholders` | All parameter types | `{string}`, `{int}`, `{double}`, `{word}` via auto-detection and outlines |
| 5 | `ex05_outline` | Scenario Outline | `<variable>` tokens, `Examples` table, quoted vs unquoted |
| 6 | `ex06_tables` | Data Tables | `\| column \|` tables, auto-generated Row data class |
| 7 | `ex07_placeholder_type` | @BehaveType — placeholder | Single `{token}` → custom type with `parameterType()` |
| 10 | `ex10_typed_table` | DataTable + @BehaveType | Typed DataTable rows with composite types |
| 11 | `ex11_cast` | @BehaveCast | Lossy conversion (`Int` receiving decimals) |
| 12 | `ex12_tags` | Tags | `@tag` filtering, boolean expressions (`and`/`or`/`not`) |
| 13 | `ex13_hooks` | Hooks | `Before`/`After` with context and `ScenarioInfo` |
| 14 | `ex14_collisions` | Name collisions | Numeric suffix resolution for duplicate method names |
| 15 | `ex15_integration` | Full integration | End-to-end: all features combined |
