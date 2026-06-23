# Gherkin Support Audit & Implementation Guide

> Audyt pokrycia języka Gherkin w `kotlin-behave` oraz analiza pluginu IntelliJ.
> Data: 2026-06. Dotyczy dwóch parserów biblioteki + parsera pluginu.

## Architektura parserów (stan obecny)

Istnieją **trzy** niezależne implementacje parsowania Gherkina:

| Parser | Plik | Rola |
|---|---|---|
| KSP (compile-time) | `ksp/.../FeatureFileParser.kt` (305 l.) | generacja `*StepsSpec` z `.feature` |
| Runtime | `core/.../parser/GherkinParser.kt` | wykonanie scenariuszy w testach |
| Plugin IntelliJ | `kotlin-behave-intellij-plugin/.../idea/FeatureFileParser.kt` (203 l.) + PSI lexer | podpowiedzi/nawigacja w IDE |

**Każdy parser obsługuje nieco inny podzbiór Gherkina** — to źródło niespójności (patrz sekcja „Plugin”).

---

## 1. Tabela pokrycia

Legenda: ✅ pełne · ⚠️ częściowe/bug · ❌ brak

| # | Konstrukcja | KSP | Runtime | Plugin | Dowód |
|---|---|:--:|:--:|:--:|---|
| 1 | `Feature:` | ✅ | ✅ | ✅ | `FeatureFileParser.kt:56`, `GherkinParser.kt:68` |
| 2 | `Background:` | ✅ | ✅ | ✅ | `FeatureFileParser.kt:116`, `GherkinParser.kt:73` |
| 3 | `Scenario:` | ✅ | ✅ | ✅ | `FeatureFileParser.kt:106`, `GherkinParser.kt:85` |
| 4 | `Scenario Outline:` | ✅ | ✅ | ✅ | `FeatureFileParser.kt:89`, `GherkinParser.kt:78` |
| 4b | `Scenario Template:` | ✅ | ❌ | ❓ | KSP `:92`; runtime brak → **niespójność** |
| 5 | `Examples:` | ✅ | ✅ | ✅ | `FeatureFileParser.kt:129`, `GherkinParser.kt:91` |
| 6 | Given/When/Then/And/But | ✅ | ✅ | ✅ | `FeatureFileParser.kt:40`, `GherkinParser.kt:134` |
| 7 | Data Tables | ✅ | ✅ | ✅ | `FeatureFileParser.kt:250`, `GherkinParser.kt:116` |
| 8 | Tagi `@` + dziedziczenie | ✅ | ✅ | ✅ | `GherkinParser.kt:32,49,107` |
| 9 | **Filtrowanie tagów wyrażeniem** (and/or/not/nawiasy) | — | ✅ | — | `TagFilter.kt` `parseTagFilter` |
| 10 | Komentarze `#` | ✅ | ✅ | ✅ | `FeatureFileParser.kt:211`, `GherkinParser.kt:10` |
| 11 | Inferencja typów parametrów outline | ✅ | ✅ | ❌ | `BehaveProcessor.inferVariableTypes` |
| — | — | — | — | — | — |
| 12 | **Doc Strings** (`"""` / ` ``` `) | ❌ | ❌ | ❌ | brak w obu parserach |
| 13 | **`*` (asterisk) jako keyword** | ❌ | ❌ | ❌ | brak w listach keywordów |
| 14 | **`Example:`** (synonim `Scenario:`) | ❌ | ❌ | ❌ | rozpoznawane tylko `Scenario:` |
| 15 | **`Scenarios:`** (synonim `Examples:`) | ❌ | ❌ | ❌ | tylko `Examples:` |
| 16 | **Puste komórki w tabelach** | ⚠️ bug | ⚠️ bug | ❓ | `parseTableRow` filtruje puste |
| 17 | **Escapowanie `\|` `\\` `\n` w komórkach** | ❌ | ❌ | ❌ | naiwny `split("\|")` |
| 18 | **`Rule:`** (Gherkin 6+) | ❌ | ❌ | ❌ | brak modelu i logiki |
| 19 | **i18n / `# language:`** | ❌ | ❌ | ❌ | keywordy zaszyte po angielsku |

---

## 2. Findingi i sposób naprawy

Reguła projektu: **każdy fix = feature file reprodukujący problem** (`examples/src/test/resources/features/`), potem implementacja. Wszystkie zmiany w parserach rób **w obu** (KSP + runtime), inaczej powstaje rozjazd compile-time vs runtime.

### F1 — Puste komórki w tabelach (BUG, priorytet 1)
- **Objaw:** `| a |  | c |` gubi pustą kolumnę → rozjazd nagłówków/wartości.
- **Przyczyna:** `parseTableRow` (`FeatureFileParser.kt:298-305`) kończy się `.filter { it.isNotEmpty() }`. Runtime ma analogiczne cięcie.
- **Fix:**
  1. Usuń `.filter { it.isNotEmpty() }`; zachowaj puste komórki.
  2. Uwaga na `removePrefix("|").removeSuffix("|").split("|")` — dla `||` daje poprawnie pustą komórkę po usunięciu filtra.
  3. Zsynchronizuj runtime (`GherkinParser` parsowanie `|`).
- **Test:** outline z pustą komórką + assert, że kolumna = `""`.
- **Koszt:** S.

### F2 — `Scenario Template:` w runtime (BUG spójności, priorytet 1)
- **Objaw:** KSP wygeneruje spec dla `Scenario Template:`, ale runtime go nie sparsuje → 0 scenariuszy w teście.
- **Fix:** w `GherkinParser.kt` dodaj gałąź `line.startsWith("Scenario Template:")` obok `Scenario Outline:` (ta sama logika).
- **Test:** feature z `Scenario Template:` uruchamiany przez `gherkin(...)`.
- **Koszt:** XS.

### F3 — `*` jako keyword kroku
- **Objaw:** `* the user is logged in` nie jest rozpoznany.
- **Fix:**
  1. KSP: dodaj `*` do `keywords` (`FeatureFileParser.kt:40`); w `resolveKeyword` mapuj `*` na poprzedni realny keyword (jak `And`/`But`).
  2. Runtime: w mapie keywordów (`GherkinParser.kt:134-138`) dodaj `*` → rozwiązywany do poprzedniego keywordu.
  3. `MethodNameGenerator` musi traktować `*` jak And/But (prefix usuwany).
- **Test:** scenariusz z krokami `*`.
- **Koszt:** S.

### F4 — `Example:` / `Scenarios:` (synonimy ze specyfikacji)
- **Fix:** w obu parserach dodaj `startsWith("Example:")` obok `Scenario:` oraz `startsWith("Scenarios:")` obok `Examples:`. Pilnuj kolejności prefiksów (`Scenario Outline:` / `Scenario Template:` sprawdzane przed `Scenario:`; `Examples:` przed ewentualnym `Example:` by uniknąć kolizji prefiksu).
- **Test:** feature używający `Example:` i `Scenarios:`.
- **Koszt:** XS.

### F5 — Escapowanie `\|`, `\\`, `\n` w komórkach
- **Objaw:** komórka z literalnym `|` rozbija wiersz.
- **Fix:** zastąp `split("|")` małym tokenizerem komórek: dziel po **niezescapowanym** `|`, potem un-escape `\|`→`|`, `\\`→`\`, `\n`→newline. Wspólne dla obu parserów (najlepiej w module współdzielonym — patrz sekcja Plugin).
- **Test:** tabela z `\|` i `\n` w komórce.
- **Koszt:** S.

### F6 — Doc Strings (`"""` / ` ``` `) — największa wartość funkcjonalna
- **Cel:** wieloliniowy argument kroku (JSON/payload).
- **Zmiany:**
  1. **Model:** dodaj `docString: String?` do `Step` (`model/Feature.kt`) i do `ParsedStep` (KSP).
  2. **Parser (oba):** po kroku wykryj linię otwierającą `"""` lub ` ``` `; zbieraj treść do linii zamykającej; usuń wspólne wcięcie; podłącz do poprzedniego kroku. **Ważne:** runtime obecnie globalnie wycina linie `#` (`GherkinParser.kt:10`) — wewnątrz docstringu `#` musi pozostać literałem; przenieś filtrowanie komentarzy do trybu „poza docstringiem”.
  3. **Codegen:** gdy krok ma docstring, dołóż parametr `docString: String` na końcu sygnatury metody; w runtime przekaż treść do handlera.
  4. **StepBuilder/runtime:** przekaż docstring jako dodatkowy argument obok `params`.
  5. **Plugin:** już istnieje `GherkinMultilineInlayProvider` — zsynchronizuj.
- **Test:** krok z docstringiem JSON; assert treści (z zachowaniem `\n` i `#`).
- **Koszt:** M.

### F7 — `Rule:` (Gherkin 6+)
- **Cel:** grupowanie scenariuszy + Background per Rule.
- **Zmiany:** model `Rule(name, background, scenarios)`; parser rozpoznaje `Rule:` i przypisuje kolejne scenariusze/Background do reguły; codegen może spłaszczać Rule (nazwa w opisie scenariusza) jeśli nie chcemy zmieniać API.
- **Test:** feature z dwiema `Rule:` i Background w każdej.
- **Koszt:** L.

### F8 — i18n / `# language:`
- **Cel:** nieangielskie keywordy.
- **Zmiany:** parsuj nagłówek `# language: pl`; załaduj tablicę keywordów per locale (format i18n z gherkin); mapuj zlokalizowane słowa na kanoniczne przed dalszą logiką. Dotyczy obu parserów + lexera pluginu.
- **Koszt:** L (najmniejszy zwrot dla projektu anglojęzycznego).

### Kolejność rekomendowana
`F1, F2` (bugi) → `F3, F4` (taniochy) → `F5` → `F6` (Doc Strings) → `F7` → `F8`.

---

## 3. Plugin IntelliJ — czy używa biblioteki?

**Odpowiedź: nie. Plugin parsuje Gherkina ręcznie, własnymi, rozjeżdżającymi się kopiami.**

### Fakty
- Zależność od biblioteki istnieje **tylko w testach** (composite build):
  `testImplementation(...core)`, `kspTest(...ksp)` — `build.gradle.kts:33-37`.
  **Kod produkcyjny pluginu nie zależy od `:ksp` ani `:core`.**
- Plugin ma własne kopie semantyczne:
  - `idea/FeatureFileParser.kt` (203 l.) — osobny, inny model (`Scenario.isOutline`, `examplesHeaders`, `ExampleHeader`) niż biblioteczny (305 l.).
  - `idea/MethodNameGenerator.kt` (39 l.) — re-implementacja, w komentarzu wprost „mirroring `io.mcol.behave.ksp.MethodNameGenerator`”.
  - własny lexer/parser PSI (`GherkinLexer`, `GherkinParserDefinition`, `GherkinScenarioScanner`) — to akurat **wymóg platformy IntelliJ** (PSI), nieuniknione.

### Dlaczego to ryzykowne (konkretne rozjazdy)
1. **Nazwy metod dla kolizji.** Biblioteczny `MethodNameGenerator` ma `resolveCollisions()` (sufiksy `foo`, `foo2`); kopia w pluginie ma **tylko `generate()`**, bez resolucji kolizji. Dla dwóch kroków o tej samej bazie plugin policzy inną nazwę niż KSP → **psuje nawigację „go to step”, generację stubów i inspekcję „orphaned step”**.
2. **Inferencja typów parametrów.** Świeżo dodana do KSP (`<n>` numeryczne → `Int`, `{int}`/`{boolean}`/...). Parser pluginu jej nie ma → inlay hints / stuby pokażą `String`, gdzie KSP generuje `Int`/`Boolean`/`Long`/`Double`.
3. **Każdy przyszły fix Gherkina (F1–F8) trzeba robić dwa razy** i ręcznie utrzymywać zgodność.
4. **Klasy biblioteczne są `internal`** (`MethodNameGenerator`, `FeatureFileParser`) — plugin fizycznie nie może ich użyć, nawet gdyby dodał zależność. To wymusiło kopiowanie.

### Rekomendacja architektoniczna — czy lepiej dzielić algorytmy? TAK.

Wyodrębnić **współdzielony, platformowo-neutralny moduł** (czysty Kotlin, **bez** zależności KSP i IntelliJ), np. `:gherkin-core`, zawierający warstwę *semantyczną*:

- `FeatureFileParser` (model + parsowanie tekstu `.feature`)
- `MethodNameGenerator` **z** `resolveCollisions`
- inferencja typów/placeholderów (`inferVariableTypes`, mapowanie `{int}`→`Int`, …)
- normalizacja kroków (`normalise`), tokenizer komórek tabel (F5)

Następnie:
- `:ksp` zależy od `:gherkin-core` zamiast trzymać własne kopie.
- **Plugin** zależy od `:gherkin-core` (zwykła zależność JVM — bez problemu, w przeciwieństwie do `:ksp`, który ciągnie API procesora KSP) i **usuwa** `idea/FeatureFileParser.kt` oraz `idea/MethodNameGenerator.kt`.
- Symbole w `:gherkin-core` zmienić z `internal` na `public`.
- **Warstwa PSI (lexer/parser IntelliJ) zostaje w pluginie** — to wymóg platformy — ale całe *nazewnictwo metod, typowanie i normalizację* deleguje do `:gherkin-core`. Dzięki temu PSI odpowiada tylko za tokeny/podświetlanie, a logika „jak KSP nazwie/otypuje krok” ma **jedno źródło prawdy**.

### Plan migracji (przyrostowy, bezpieczny)
1. Utwórz `:gherkin-core` (pure Kotlin/JVM lub KMP `commonMain`).
2. Przenieś tam `MethodNameGenerator` (z `resolveCollisions`) + testy; zmień na `public`. `:ksp` importuje.
3. Przenieś `FeatureFileParser` i logikę inferencji typów; `:ksp` deleguje.
4. Dodaj zależność pluginu na `:gherkin-core`; podmień wywołania; **usuń kopie** z `idea/`.
5. Złota nić testowa: parametryzowany test porównujący `MethodNameGenerator.generate/resolveCollisions` i wynik inferencji typów na zestawie feature’ów — uruchamiany w obu modułach, gwarantuje brak rozjazdu.
6. Kolejne fixy Gherkina (F1–F8) implementuj **wyłącznie** w `:gherkin-core` → automatycznie trafiają do KSP, runtime (jeśli też zacznie z niego korzystać) i pluginu.

**Korzyść:** jeden algorytm = brak rozjazdu nazw/typów między IDE a kompilacją, połowa kodu do utrzymania, każdy przyszły feature Gherkina robiony raz.

---

## 4. Stan wdrożenia współdzielenia

### ✅ Faza 1 — `MethodNameGenerator` (zrobione)
- Nowy moduł **`:gherkin-shared`** (`kotlin-behave/gherkin-shared/`, czysty Kotlin/JVM, `behave.publish`, coords `io.mcol.kotlin-behave:gherkin-shared:0.1.0`).
- `MethodNameGenerator` przeniesiony tam jako `public` (z `generate(keyword,text)`, overloadem `generate(keywordAndText)` i `resolveCollisions`). Test przeniesiony do `:gherkin-shared`.
- `:ksp` zależy od `:gherkin-shared` (usunięto kopię z `ksp/`).
- **Plugin** zależy od `:gherkin-shared` (`implementation`, composite build), usunięto `idea/MethodNameGenerator.kt`, wszystkie 8 konsumentów + testy przepięte na `io.mcol.behave.gherkin.MethodNameGenerator`.
- Build zielony: `kotlin-behave` (test+detekt+spotless) oraz plugin (`compileKotlin`/`compileTestKotlin`).
- **Efekt:** IDE i KSP liczą nazwy metod tym samym algorytmem (koniec rozjazdu np. przy literałach liczbowych).

### ✅ Faza 2a — inferencja typów wyniesiona do `:gherkin-shared` (zrobione)
- Nowy `GherkinTypes` w `:gherkin-shared`: `inferVariableTypes(templateText, instanceTexts)` + mapy `placeholderToKotlin` / `kotlinToPlaceholder`.
- `BehaveProcessor.inferVariableTypes` deleguje do `GherkinTypes`; `CodeGenerator.builtinTypes` i `replaceOutlineVariablesTyped` korzystają z map współdzielonych (jedno źródło prawdy).
- Dodano **bezpośrednie testy jednostkowe** inferencji (Int/Long/Double/Boolean, mixed→String, multi-kolumna, unifikacja tabela+standalone, quoted) — wcześniej pokryte tylko pośrednio.
- Build zielony (kotlin-behave `build` z examples ex20 + delegacją).
- `GherkinTypes` jest już na classpath pluginu (zależność z Fazy 1) — gotowe do użycia.

### ✅ Faza 2b — inlay typów parametrów w pluginie (zrobione)
- `GherkinParamTypeInlayProvider` dla `<outline var>` nie pokazuje już na sztywno `: String` — liczy typ z kolumny `Examples` przez współdzielony `GherkinTypes.inferType` (ta sama logika co KSP): `: Int` / `: Double` / `: Boolean` / `: Long`, a `: String` gdy kolumna jest tekstowa/mieszana.
- Dodano `GherkinTypes.inferType(values)` w `:gherkin-shared` (decyzja o typie w bibliotece). Po stronie pluginu jedynie trywialny, czysty skan kolumny (`exampleColumnValues`) ograniczony stabilnym `GherkinScenarioScanner` — bez zależności od WIP.
- Testy jednostkowe bez IDE: 7 przypadków po stronie pluginu + testy `inferType` w bibliotece.

### ⏳ Faza 2c — pozostałe (do zrobienia)
- **`resolveCollisions` w pluginie:** funkcja dostępna, ale konsumenci wołają samo `generate()` per-krok. Dla kolidujących kroków liczyć nazwy z `resolveCollisions` na całym feature.
- **`FeatureFileParser` / normalizacja / tokenizer komórek** — wynieść warstwę semantyczną do `:gherkin-shared`; parser PSI w pluginie zostaje, ale deleguje (pozwoli też zastąpić lokalny skan `exampleColumnValues`).
- **Złota nić testowa:** wspólny zestaw przypadków uruchamiany po obu stronach.

### Zasada na przyszłość
Każdy nowy element obsługi Gherkina (F1–F8 powyżej) implementujemy **w `:gherkin-shared`**, nie ręcznie w pluginie — wtedy automatycznie trafia do KSP i IDE.
