# language: de
# Example 25: i18n / localized keywords. The `# language:` header selects a Gherkin dialect;
# the structural keywords (Funktionalität/Grundlage/Szenario/Szenariogrundriss/Beispiele and
# Angenommen/Wenn/Dann/Und) are German. Only keywords are localized — the step *text* stays in
# the author's language, so step definitions match the German text. Parsed AND run end-to-end.

Funktionalität: Lokalisierte Schlüsselwörter

  Grundlage:
    Angenommen ein Zähler bei 0

  Szenario: Zweimal hochzählen
    Wenn ich den Zähler erhöhe
    Und ich den Zähler erhöhe
    Dann ist der Zähler 2

  Szenariogrundriss: Mehrfaches Hochzählen
    Wenn ich den Zähler <anzahl> mal erhöhe
    Dann ist der Zähler <gesamt>

    Beispiele:
      | anzahl | gesamt |
      | 3      | 3      |
      | 5      | 5      |
