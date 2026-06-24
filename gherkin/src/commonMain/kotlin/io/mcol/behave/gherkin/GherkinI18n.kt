package io.mcol.behave.gherkin

/**
 * Gherkin internationalization: detect a feature file's `# language:` header and rewrite its
 * localized keywords to canonical English so the existing English-only parsers handle every
 * dialect unchanged. Only structural keywords are translated — step *text*, names, tables and doc
 * strings are left verbatim. English input is returned untouched (zero-cost, zero-risk fast path).
 *
 * Shared by :core (runtime) and :ksp so compile-time names and runtime execution agree.
 */
public object GherkinI18n {
    /** A localized keyword prefix (`source`, including its trailing `:`/space) and its English form. */
    private data class Rule(val source: String, val canonical: String)

    /**
     * The dialect code from the leading `# language: xx` comment, lowercased; [GherkinDialects.DEFAULT_LANGUAGE]
     * when absent. Only the leading comment block is consulted — the first content line ends the search.
     */
    public fun languageOf(featureText: String): String {
        for (raw in featureText.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("#")) break
            val body = line.removePrefix("#").trim()
            if (body.startsWith("language:")) {
                return body.removePrefix("language:").trim().takeWhile { !it.isWhitespace() }.lowercase()
            }
        }
        return GherkinDialects.DEFAULT_LANGUAGE
    }

    /**
     * Rewrite [featureText] so every localized keyword becomes its canonical English keyword. Returns
     * the input unchanged for English, an unknown language, or text with no header.
     */
    public fun toCanonical(featureText: String): String {
        val language = languageOf(featureText)
        if (language == GherkinDialects.DEFAULT_LANGUAGE) return featureText
        val dialect = GherkinDialects.forLanguage(language) ?: return featureText
        val rules = rulesFor(dialect)

        val out = ArrayList<String>()
        var docFence: String? = null
        for (raw in featureText.lines()) {
            val trimmed = raw.trim()
            when {
                docFence != null -> {
                    if (trimmed == docFence) docFence = null
                    out.add(raw)
                }
                trimmed.startsWith("\"\"\"") || trimmed.startsWith("```") -> {
                    docFence = if (trimmed.startsWith("```")) "```" else "\"\"\""
                    out.add(raw)
                }
                trimmed.startsWith("#") -> out.add(raw)
                else -> out.add(translateLine(raw, rules))
            }
        }
        return out.joinToString("\n")
    }

    /** Replace the first matching localized keyword at the start of [raw], preserving indentation. */
    private fun translateLine(raw: String, rules: List<Rule>): String {
        val rest = raw.trimStart()
        val indent = raw.substring(0, raw.length - rest.length)
        val rule = rules.firstOrNull { rest.startsWith(it.source) } ?: return raw
        return indent + rule.canonical + rest.substring(rule.source.length)
    }

    /** Build the longest-first replacement list so multi-word keywords win over their prefixes. */
    private fun rulesFor(dialect: GherkinDialect): List<Rule> {
        val rules = ArrayList<Rule>()
        block(dialect.feature, "Feature", rules)
        block(dialect.background, "Background", rules)
        block(dialect.rule, "Rule", rules)
        block(dialect.scenarioOutline, "Scenario Outline", rules)
        block(dialect.scenario, "Scenario", rules)
        block(dialect.examples, "Examples", rules)
        step(dialect.given, "Given", rules)
        step(dialect.whenKw, "When", rules)
        step(dialect.then, "Then", rules)
        step(dialect.and, "And", rules)
        step(dialect.but, "But", rules)
        return rules.sortedByDescending { it.source.length }
    }

    /** Block keywords are matched with a trailing `:`. */
    private fun block(keywords: List<String>, canonical: String, into: MutableList<Rule>) {
        for (kw in keywords) into.add(Rule("$kw:", "$canonical:"))
    }

    /** Step keywords are matched with a trailing space. */
    private fun step(keywords: List<String>, canonical: String, into: MutableList<Rule>) {
        for (kw in keywords) into.add(Rule("$kw ", "$canonical "))
    }
}
