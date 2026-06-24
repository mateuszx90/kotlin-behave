package io.mcol.behave.gherkin

import kotlin.test.Test
import kotlin.test.assertEquals

class GherkinI18nTest {

    @Test
    fun `languageOf reads the language header`() {
        assertEquals("de", GherkinI18n.languageOf("# language: de\nFunktionalität: x"))
    }

    @Test
    fun `languageOf tolerates no space and surrounding whitespace`() {
        assertEquals("fr", GherkinI18n.languageOf("  #language:   fr  \nFonctionnalité: x"))
    }

    @Test
    fun `languageOf defaults to en when no header is present`() {
        assertEquals("en", GherkinI18n.languageOf("Feature: x\n  Scenario: s"))
    }

    @Test
    fun `languageOf stops at the first non-comment line`() {
        // A language header only counts in the leading comment block.
        assertEquals("en", GherkinI18n.languageOf("Feature: x\n# language: de"))
    }

    @Test
    fun `toCanonical rewrites German block and step keywords`() {
        val input = """
            # language: de
            Funktionalität: F
              Grundlage:
                Angenommen a
              Szenario: S
                Wenn b
                Und c
                Dann d
        """.trimIndent()
        val out = GherkinI18n.toCanonical(input)
        assertEquals(
            listOf(
                "# language: de",
                "Feature: F",
                "  Background:",
                "    Given a",
                "  Scenario: S",
                "    When b",
                "    And c",
                "    Then d",
            ),
            out.lines(),
        )
    }

    @Test
    fun `toCanonical maps outline and examples synonyms`() {
        val input = "# language: de\nSzenariogrundriss: x\n  Beispiele:"
        assertEquals(listOf("# language: de", "Scenario Outline: x", "  Examples:"), GherkinI18n.toCanonical(input).lines())
    }

    @Test
    fun `toCanonical prefers the longer matching keyword`() {
        // "Gegeben seien" (a Given synonym) must win over the shorter "Gegeben sei".
        val out = GherkinI18n.toCanonical("# language: de\nSzenario: s\n  Gegeben seien x")
        assertEquals("  Given x", out.lines()[2])
    }

    @Test
    fun `toCanonical returns English input unchanged`() {
        val input = "Feature: F\n  Scenario: S\n    Given a\n    When b\n    Then c"
        assertEquals(input, GherkinI18n.toCanonical(input))
    }

    @Test
    fun `toCanonical does not translate inside doc strings`() {
        val input = "# language: de\nSzenario: s\n  Wenn x:\n    ```\n    Wenn literal\n    ```\n  Dann y"
        val lines = GherkinI18n.toCanonical(input).lines()
        assertEquals("Scenario: s", lines[1])
        assertEquals("  When x:", lines[2])
        assertEquals("    Wenn literal", lines[4]) // inside doc string — untranslated
        assertEquals("  Then y", lines[6])
    }

    @Test
    fun `unknown language is left unchanged`() {
        val input = "# language: zz\nFunktionalität: F"
        assertEquals(input, GherkinI18n.toCanonical(input))
    }
}
