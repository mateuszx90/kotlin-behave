package io.mcol.behave.gherkin

/**
 * Localized keyword sets for one Gherkin dialect. Each field lists every spelling that maps to the
 * canonical English construct. Block keywords ([feature], [background], [rule], [scenarioOutline],
 * [scenario], [examples]) are written in feature files followed by `:`; step keywords ([given],
 * [whenKw], [then], [and], [but]) are followed by a space. The `*` bullet is language-independent.
 */
public data class GherkinDialect(
    val feature: List<String>,
    val background: List<String>,
    val rule: List<String>,
    val scenarioOutline: List<String>,
    val scenario: List<String>,
    val examples: List<String>,
    val given: List<String>,
    val whenKw: List<String>,
    val then: List<String>,
    val and: List<String>,
    val but: List<String>,
)

/**
 * Single source of truth for Gherkin dialects, shared by the runtime parser (:core) and the KSP
 * processor (:ksp) so a localized feature file produces identical results at compile time and at
 * runtime. English is the default; new dialects are pure data added to [byCode].
 */
public object GherkinDialects {
    public const val DEFAULT_LANGUAGE: String = "en"

    public val byCode: Map<String, GherkinDialect> =
        mapOf(
            "en" to GherkinDialect(
                feature = listOf("Feature", "Business Need", "Ability"),
                background = listOf("Background"),
                rule = listOf("Rule"),
                scenarioOutline = listOf("Scenario Outline", "Scenario Template"),
                scenario = listOf("Scenario", "Example"),
                examples = listOf("Examples", "Scenarios"),
                given = listOf("Given"),
                whenKw = listOf("When"),
                then = listOf("Then"),
                and = listOf("And"),
                but = listOf("But"),
            ),
            "de" to GherkinDialect(
                feature = listOf("Funktionalität", "Funktion"),
                background = listOf("Grundlage", "Hintergrund", "Voraussetzungen", "Vorbedingungen"),
                rule = listOf("Regel"),
                scenarioOutline = listOf("Szenariogrundriss"),
                scenario = listOf("Beispiel", "Szenario"),
                examples = listOf("Beispiele"),
                given = listOf("Angenommen", "Gegeben seien", "Gegeben sei"),
                whenKw = listOf("Wenn"),
                then = listOf("Dann"),
                and = listOf("Und"),
                but = listOf("Aber"),
            ),
            "fr" to GherkinDialect(
                feature = listOf("Fonctionnalité"),
                background = listOf("Contexte"),
                rule = listOf("Règle"),
                scenarioOutline = listOf("Plan du scénario", "Plan du Scénario"),
                scenario = listOf("Exemple", "Scénario"),
                examples = listOf("Exemples"),
                given = listOf("Étant donné que", "Étant donné", "Etant donné que", "Etant donné", "Soit"),
                whenKw = listOf("Lorsque", "Quand"),
                then = listOf("Alors"),
                and = listOf("Et"),
                but = listOf("Mais"),
            ),
            "es" to GherkinDialect(
                feature = listOf("Característica"),
                background = listOf("Antecedentes"),
                rule = listOf("Regla"),
                scenarioOutline = listOf("Esquema del escenario"),
                scenario = listOf("Ejemplo", "Escenario"),
                examples = listOf("Ejemplos"),
                given = listOf("Dados", "Dadas", "Dado", "Dada"),
                whenKw = listOf("Cuando"),
                then = listOf("Entonces"),
                and = listOf("Y", "E"),
                but = listOf("Pero"),
            ),
            "pl" to GherkinDialect(
                feature = listOf("Właściwość", "Funkcja", "Aspekt", "Potrzeba biznesowa"),
                background = listOf("Założenia"),
                rule = listOf("Zasada"),
                scenarioOutline = listOf("Szablon scenariusza"),
                scenario = listOf("Przykład", "Scenariusz"),
                examples = listOf("Przykłady"),
                given = listOf("Zakładając, że", "Zakładając", "Mając"),
                whenKw = listOf("Jeżeli", "Jeśli", "Kiedy", "Gdy"),
                then = listOf("Wtedy"),
                and = listOf("Oraz", "I"),
                but = listOf("Ale"),
            ),
        )

    public fun forLanguage(code: String): GherkinDialect? = byCode[code]
}
