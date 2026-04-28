package io.mcol.behave.runner

sealed interface TagFilter {
    data class Tag(
        val name: String,
    ) : TagFilter

    data class And(
        val left: TagFilter,
        val right: TagFilter,
    ) : TagFilter

    data class Or(
        val left: TagFilter,
        val right: TagFilter,
    ) : TagFilter

    data class Not(
        val operand: TagFilter,
    ) : TagFilter
}

fun TagFilter.matches(tags: Set<String>): Boolean = when (this) {
    is TagFilter.Tag -> name in tags
    is TagFilter.And -> left.matches(tags) && right.matches(tags)
    is TagFilter.Or -> left.matches(tags) || right.matches(tags)
    is TagFilter.Not -> !operand.matches(tags)
}

/**
 * Parses a boolean tag expression into a TagFilter.
 * Supports: @tag, and, or, not, parentheses. Operators are case-insensitive.
 * Examples:
 *   "@smoke"
 *   "@smoke and not @wip"
 *   "(@smoke or @auth) and not @slow"
 */
fun parseTagFilter(expression: String): TagFilter {
    val tokens = tokenize(expression)
    val parser = TagExpressionParser(tokens)
    return parser.parseOr()
}

private fun tokenize(expr: String): List<String> {
    val result = mutableListOf<String>()
    var i = 0
    val s = expr.trim()
    while (i < s.length) {
        when {
            s[i].isWhitespace() -> i++
            s[i] == '(' -> {
                result.add("(")
                i++
            }
            s[i] == ')' -> {
                result.add(")")
                i++
            }
            s[i] == '@' -> {
                var j = i + 1
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '-' || s[j] == '_')) j++
                result.add(s.substring(i, j))
                i = j
            }
            s[i].isLetter() -> {
                var j = i
                while (j < s.length && s[j].isLetter()) j++
                result.add(s.substring(i, j))
                i = j
            }
            else -> i++
        }
    }
    return result
}

private class TagExpressionParser(
    private val tokens: List<String>,
) {
    private var pos = 0

    private fun peek(): String? = tokens.getOrNull(pos)

    private fun consume(): String = tokens[pos++]

    fun parseOr(): TagFilter {
        var left = parseAnd()
        while (peek()?.lowercase() == "or") {
            consume()
            val right = parseAnd()
            left = TagFilter.Or(left, right)
        }
        return left
    }

    fun parseAnd(): TagFilter {
        var left = parseNot()
        while (peek()?.lowercase() == "and") {
            consume()
            val right = parseNot()
            left = TagFilter.And(left, right)
        }
        return left
    }

    fun parseNot(): TagFilter {
        if (peek()?.lowercase() == "not") {
            consume()
            return TagFilter.Not(parseNot())
        }
        return parsePrimary()
    }

    fun parsePrimary(): TagFilter {
        val token = peek() ?: error("Unexpected end of tag expression")
        return when {
            token == "(" -> {
                consume()
                val inner = parseOr()
                if (peek() != ")") error("Expected ')' in tag expression")
                consume()
                inner
            }
            token.startsWith("@") -> {
                consume()
                TagFilter.Tag(token)
            }
            else -> error("Unexpected token in tag expression: '$token'")
        }
    }
}
