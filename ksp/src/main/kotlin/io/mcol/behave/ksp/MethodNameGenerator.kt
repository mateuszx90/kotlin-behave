package io.mcol.behave.ksp

/**
 * Generates camelCase method names from Gherkin step text.
 *
 * Rules:
 * 1. Keyword (Given/When/Then/And/But) is kept as a lowercase prefix followed by capitalised words
 * 2. {placeholder} tokens are stripped from the name
 * 3. <variable> tokens are stripped from the name
 * 4. Trailing colon is stripped
 * 5. Remaining words are joined in camelCase
 * 6. Collisions get numeric suffix: name0, name1, ...
 */
internal object MethodNameGenerator {

    private val QUOTED_LITERAL_REGEX = Regex("\"[^\"]*\"")
    private val PLACEHOLDER_REGEX = Regex("\\{[^}]+}")
    private val VARIABLE_REGEX = Regex("<[^>]+>")
    private val DOUBLE_LITERAL_REGEX = Regex("""(?<!\S)-?\d+\.\d+(?!\S)""")
    private val INT_LITERAL_REGEX = Regex("""(?<!\S)-?\d+(?!\S)""")
    private val NON_ALNUM = Regex("[^a-zA-Z0-9]+")

    /**
     * Generate a method name from a keyword and step text.
     * Does not handle collision — call [resolveCollisions] after generating all names.
     */
    fun generate(keyword: String, text: String): String {
        // Strip quoted literals ("value"), placeholders, and outline variables
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

        // keyword + remaining words in camelCase
        val allWords = listOf(keyword) + words
        return allWords.mapIndexed { i, w ->
            if (i == 0) w.lowercase() else w.replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }

    /**
     * Given a list of (keyword, text) pairs, generate method names with collision suffixes applied.
     * Returns a list of method names in the same order as input.
     */
    fun resolveCollisions(steps: List<Pair<String, String>>): List<String> {
        val raw = steps.map { (kw, text) -> generate(kw, text) }
        val counts = raw.groupBy { it }.mapValues { it.value.size }
        val indices = mutableMapOf<String, Int>()
        return raw.map { name ->
            if (counts[name]!! > 1) {
                val idx = indices.getOrDefault(name, 0)
                indices[name] = idx + 1
                "$name$idx"
            } else {
                name
            }
        }
    }
}
