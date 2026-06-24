# Architecture & Diagrams

[← Back to README](../README.md)

How the pieces fit together — build-time generation, the test-writing loop, and the
per-scenario lifecycle.

## Modules

| Module | Purpose | Targets |
|--------|---------|---------|
| `:core` | Runtime: step builder, Gherkin parser, runner, type registry | JVM, JS, iOS, macOS, Linux |
| `:kotest` | Kotest FreeSpec integration | JVM, JS, iOS, macOS, Linux |
| `:annotations` | `@BehaveFeature`, `@Type`, `@TypeConverter`, `@BehaveType`, `@BehaveCast`, `@StepsMixin`, `@DivergentStep` (compile-only) | JVM, JS, iOS, macOS, Linux |
| `:ksp` | KSP processor — generates `*StepsSpec` interfaces at build time | JVM |

## Full setup flow

```mermaid
graph LR
    subgraph Build Time
        F[".feature file"] -->|read by| KSP["KSP Processor<br/>:ksp"]
        A["@BehaveFeature<br/>:annotations"] -->|scanned by| KSP
        KSP -->|generates| SPEC["*StepsSpec<br/>interface"]
        KSP -->|generates| VAL["val generated*Steps<br/>instance"]
        KSP -->|generates| TEST["*GherkinTest<br/>FreeSpec class"]
    end

    subgraph Your Code
        STEPS["*Steps class"] -->|implements| SPEC
    end

    subgraph Runtime
        TEST -->|uses| VAL
        VAL -->|wires| STEPS
        VAL -->|registers in| CORE["Step Builder<br/>+ Type Registry<br/>:core"]
        CORE -->|parsed by| PARSER["Gherkin Parser"]
        PARSER -->|executed by| RUNNER["Gherkin Runner"]
        RUNNER -->|reported via| KOTEST["Kotest FreeSpec<br/>:kotest"]
    end
```

## Test-writing flow

```mermaid
flowchart TD
    A["1 · Write .feature file"] --> B["2 · Annotate Steps class<br/>with @BehaveFeature"]
    B --> C{"./gradlew build"}
    C --> D["KSP reads feature file<br/>+ annotations"]
    D --> E["Generates *StepsSpec interface<br/>(one method per unique step)"]
    D --> F["Generates val + GherkinTest<br/>(auto wiring)"]
    E --> G["3 · Implement *StepsSpec<br/>in your Steps class"]
    G --> H{"Compile"}
    H -->|"Missing step?"| I["Compiler error ✗<br/>— add the override"]
    I --> G
    H -->|"All steps implemented"| J{"./gradlew test"}
    J --> K["GherkinParser expands<br/>Outlines + Examples"]
    K --> L["GherkinRunner executes<br/>Background → Steps"]
    L --> M["Kotest reports<br/>pass / fail per scenario"]
```

## Scenario lifecycle

```mermaid
sequenceDiagram
    participant R as GherkinRunner
    participant SR as ScenarioRunner
    participant H as Hooks
    participant S as Steps

    R->>SR: runScenario(ctx, run)
    activate SR
    SR->>H: beforeScenario()
    loop Each Background Step
        H->>S: step method()
    end
    loop Each Scenario Step
        H->>S: step method()
    end
    SR->>H: afterScenario(info)
    deactivate SR
    Note right of H: info contains name,<br/>tags, status
```
