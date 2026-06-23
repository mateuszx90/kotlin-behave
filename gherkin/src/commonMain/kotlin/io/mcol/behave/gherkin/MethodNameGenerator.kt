package io.mcol.behave.gherkin

/**
 * Generates camelCase method names from Gherkin step text.
 *
 * This is the SINGLE SOURCE OF TRUTH for step→method-name mapping, shared by the KSP
 * processor (which generates the methods) and the IntelliJ plugin (which must compute the
 * same identifier for navigation, stub generation and inspections). Do not reimplement it
 * anywhere else — depend on this module instead.
 *
 * Rules:
 * 1. Keyword (Given/When/Then/And/But or `*`) is NOT included — method names are keyword-agnostic
 * 2. {placeholder} tokens are stripped from the name
 * 3. <variable> tokens are stripped from the name
 * 4. Standalone number literals are stripped
 * 5. Trailing colon is stripped
 * 6. Remaining words are joined in camelCase
 * 7. Collisions get a numeric suffix via [resolveCollisions]: name0, name1, ...
 */
public object MethodNameGenerator {
    private val QUOTED_LITERAL_REGEX = Regex("\"[^\"]*\"")
    private val PLACEHOLDER_REGEX = Regex("\\{[^}]+\\}")
    private val VARIABLE_REGEX = Regex("<[^>]+>")
    private val DOUBLE_LITERAL_REGEX = Regex("""(?<!\S)-?\d+\.\d+(?!\S)""")
    private val INT_LITERAL_REGEX = Regex("""(?<!\S)-?\d+(?!\S)""")
    private val NON_ALNUM = Regex("[^a-zA-Z0-9]+")

    /**
     * Generate a method name from step text (keyword is ignored).
     * Does not handle collisions — call [resolveCollisions] after generating all names.
     */
    public fun generate(
        keyword: String,
        text: String,
    ): String {
        // Strip quoted literals ("value"), placeholders, outline variables and number literals
        var clean = text
        clean = QUOTED_LITERAL_REGEX.replace(clean, " ")
        clean = PLACEHOLDER_REGEX.replace(clean, " ")
        clean = VARIABLE_REGEX.replace(clean, " ")
        clean = DOUBLE_LITERAL_REGEX.replace(clean, " ")
        clean = INT_LITERAL_REGEX.replace(clean, " ")
        // Strip trailing colon
        clean = clean.trimEnd(':')
        // Split into words
        val words = NON_ALNUM.split(clean).filter { it.isNotBlank() }
        if (words.isEmpty()) return "step"

        // camelCase without keyword prefix
        return words
            .mapIndexed { i, w ->
                if (i == 0) w.replaceFirstChar { it.lowercase() } else w.replaceFirstChar { it.uppercase() }
            }.joinToString("")
    }

    /**
     * Convenience overload: split a single `"Given I do something"` string into keyword + text.
     * The keyword is whatever precedes the first whitespace; it is ignored by [generate] anyway.
     */
    public fun generate(keywordAndText: String): String {
        val keyword = keywordAndText.takeWhile { !it.isWhitespace() }
        val text = keywordAndText.dropWhile { !it.isWhitespace() }.trim()
        return generate(keyword, text)
    }

    /**
     * Given a list of (keyword, text) pairs, generate method names with collision suffixes applied.
     * Returns a list of method names in the same order as input.
     */
    public fun resolveCollisions(steps: List<Pair<String, String>>): List<String> {
        val raw = steps.map { (kw, text) -> generate(kw, text) }
        val counts = raw.groupBy { it }.mapValues { it.value.size }
        val indices = mutableMapOf<String, Int>()
        return raw.map { name ->
            if (counts[name]!! > 1) {
                val idx = indices[name] ?: 0
                indices[name] = idx + 1
                "$name$idx"
            } else {
                name
            }
        }
    }
}
