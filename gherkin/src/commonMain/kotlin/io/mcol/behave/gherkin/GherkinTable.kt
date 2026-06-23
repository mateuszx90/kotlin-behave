package io.mcol.behave.gherkin

/**
 * Shared Gherkin table-row tokenizer, used by both the runtime parser (:core) and the KSP
 * processor (:ksp) so they split rows identically. Single source of truth.
 */
public object GherkinTable {
    /**
     * Split a `| a | b |` row into trimmed cells. Border pipes are stripped first, so empty cells
     * (`| a |  | c |`) are PRESERVED — dropping them would misalign columns with the header.
     *
     * Only the delimiter and the escape char itself are unescaped: `\|` -> `|`, `\\` -> `\`.
     * `\n` is left LITERAL (the two characters), because Examples values are substituted into step
     * text verbatim and steps commonly convert `\n` to a newline themselves; turning it into a real
     * newline here would make the value whitespace and break `{word}`/`{string}` matching.
     */
    public fun splitRow(line: String): List<String> {
        val inner = line.trim().removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            when {
                c == '\\' && i + 1 < inner.length -> {
                    when (inner[i + 1]) {
                        '|' -> sb.append('|')
                        '\\' -> sb.append('\\')
                        else -> sb.append(c).append(inner[i + 1])
                    }
                    i += 2
                }
                c == '|' -> {
                    cells.add(sb.toString().trim())
                    sb.clear()
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        cells.add(sb.toString().trim())
        return cells
    }
}
