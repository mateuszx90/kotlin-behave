# Third-Party Software

`kotlin-behave` is licensed under [Apache License 2.0](LICENSE). It depends on and
redistributes (transitively) the third-party software listed below. All listed
licenses are compatible with Apache 2.0.

For the always-up-to-date authoritative list, run:

```
./gradlew checkLicense
open build/reports/dependency-license/third-party-licenses.html
```

## Runtime / Compile dependencies

These are surfaced to consumers of the library (via `api(...)` or transitive
exposure) and ship with published artifacts.

| Module | Coordinates | License |
|---|---|---|
| Kotlin Standard Library | `org.jetbrains.kotlin:kotlin-stdlib` | Apache 2.0 |
| Kotlin Multiplatform stdlibs | `org.jetbrains.kotlin:kotlin-stdlib-*` | Apache 2.0 |
| Kotest framework engine | `io.kotest:kotest-framework-engine` | Apache 2.0 |
| Kotlin Symbol Processing API | `com.google.devtools.ksp:symbol-processing-api` | Apache 2.0 |

## Test-only dependencies

Used by the project's own tests; not redistributed.

| Module | Coordinates | License |
|---|---|---|
| Kotlin Test | `org.jetbrains.kotlin:kotlin-test` | Apache 2.0 |
| kotlinx.coroutines test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Apache 2.0 |
| Kotest JUnit5 runner | `io.kotest:kotest-runner-junit5` | Apache 2.0 |
| JUnit 5 (transitive via Kotest runner) | `org.junit.*` | EPL 2.0 |
| OpenTest4J (transitive) | `org.opentest4j:opentest4j` | Apache 2.0 |

## Build-time only

Used by Gradle during the build; never reach consumers.

| Plugin | License |
|---|---|
| Kotest Gradle plugin (`io.kotest`) | Apache 2.0 |
| KSP Gradle plugin (`com.google.devtools.ksp`) | Apache 2.0 |
| Detekt (`io.gitlab.arturbosch.detekt`) | Apache 2.0 |
| Spotless (`com.diffplug.spotless`) | Apache 2.0 |
| Dependency License Report (`com.github.jk1.dependency-license-report`) | Apache 2.0 |

## License compatibility

Apache 2.0 is compatible (one-way, into Apache) with all dependencies listed.
EPL 2.0 (JUnit) is consumed under its "secondary license" clause permitting
redistribution under Apache 2.0.

If a dependency is added with a license outside the allowlist in
[`allowed-licenses.json`](allowed-licenses.json), `./gradlew checkLicense` will
fail and identify the offending module.
