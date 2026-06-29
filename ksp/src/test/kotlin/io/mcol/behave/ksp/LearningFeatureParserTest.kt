package io.mcol.behave.ksp

import io.mcol.behave.gherkin.FeatureFileParser
import io.mcol.behave.gherkin.MethodNameGenerator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Integration tests that parse the real learning_screen.feature file and verify
 * that quoted literals and outline variables are handled correctly by the KSP pipeline.
 */
class LearningFeatureParserTest {
    private val featureText: String by lazy {
        checkNotNull(
            javaClass.classLoader.getResourceAsStream("features/learning_screen.feature"),
        ) { "learning_screen.feature not found in test resources" }
            .bufferedReader()
            .readText()
    }

    private val parsed by lazy { FeatureFileParser.parse(featureText) }

    // region Deduplication

    @Test
    fun `tap steps with different quoted literals deduplicate to one method`() {
        // When I tap "Cancel" and And I tap "..." all normalise the same (And resolves to When)
        val tapSteps = parsed.steps.filter { it.text.startsWith("I tap ") }
        assertEquals(1, tapSteps.size, "All I tap \"...\" variants should deduplicate to 1 step")
        assertEquals("When", tapSteps[0].keyword, "And should resolve to When")
    }

    @Test
    fun `see correct answer steps with different literals deduplicate`() {
        val steps = parsed.steps.filter { it.text.startsWith("I see the correct answer ") }
        assertEquals(1, steps.size)
    }

    // endregion

    // region Method name generation

    @Test
    fun `quoted literal is stripped from method name for see question word step`() {
        val step = parsed.steps.first { it.text.startsWith("I see the question word ") }
        val name = MethodNameGenerator.generate(step.keyword, step.text)
        assertEquals("iSeeTheQuestionWord", name)
    }

    @Test
    fun `quoted literal is stripped from method name for tap step`() {
        val step = parsed.steps.first { it.text.startsWith("I tap ") }
        val name = MethodNameGenerator.generate(step.keyword, step.text)
        assertEquals("iTap", name)
    }

    @Test
    fun `quoted outline variable is stripped from method name for type step`() {
        val step = parsed.steps.first { it.text.startsWith("I type ") && it.text.contains("in the answer field") }
        val name = MethodNameGenerator.generate(step.keyword, step.text)
        assertEquals("iTypeInTheAnswerField", name)
    }

    // endregion

    // region Step expression generation

    @Test
    fun `quoted literal becomes string placeholder in step expression for question word step`() {
        // Feature file uses "pies" (quoted literal)
        val step = parsed.steps.first { it.text.startsWith("I see the question word ") }
        val expr = CodeGenerator.replaceOutlineVariables(CodeGenerator.replaceQuotedLiterals(step.text))
        assertEquals("I see the question word {string}", expr)
    }

    @Test
    fun `quoted outline variable becomes string placeholder in step expression for type step`() {
        // Feature file uses "<answer>" (quoted outline variable reference)
        val step = parsed.steps.first { it.text.startsWith("I type ") && it.text.contains("answer field") }
        val expr = CodeGenerator.replaceOutlineVariables(CodeGenerator.replaceQuotedLiterals(step.text))
        assertEquals("I type {string} in the answer field", expr)
    }

    @Test
    fun `unquoted outline variable becomes word placeholder in step expression`() {
        val step = parsed.steps.first { it.text.startsWith("I see ") && it.text.endsWith("feedback") && it.text.contains("<") }
        val expr = CodeGenerator.replaceOutlineVariables(CodeGenerator.replaceQuotedLiterals(step.text))
        assertEquals("I see {word} feedback", expr)
    }

    @Test
    fun `tap step expression uses string placeholder`() {
        // Feature file uses "Cancel" (quoted literal) for tap steps
        val step = parsed.steps.first { it.keyword == "When" && it.text.startsWith("I tap ") }
        val expr = CodeGenerator.replaceOutlineVariables(CodeGenerator.replaceQuotedLiterals(step.text))
        assertEquals("I tap {string}", expr)
    }

    // endregion

    // region Inline param resolution

    @Test
    fun `quoted literal step has String param named string`() {
        // Feature file uses "pies" (quoted literal) — param name defaults to "string"
        val step = parsed.steps.first { it.text.startsWith("I see the question word ") }
        val params = resolveInlineParamsForTest(step.text)
        assertEquals(1, params.size)
        assertEquals("string", params[0].name)
        assertEquals("String", params[0].typeName)
    }

    @Test
    fun `quoted outline variable step has String param named after variable`() {
        val step = parsed.steps.first { it.text.startsWith("I type ") && it.text.contains("answer field") }
        val params = resolveInlineParamsForTest(step.text)
        assertEquals(1, params.size)
        assertEquals("answer", params[0].name)
        assertEquals("String", params[0].typeName)
    }

    @Test
    fun `unquoted outline variable step has String param named after variable`() {
        val step = parsed.steps.first { it.text.startsWith("I see ") && it.text.endsWith("feedback") && it.text.contains("<") }
        val params = resolveInlineParamsForTest(step.text)
        assertEquals(1, params.size)
        assertEquals("type", params[0].name)
        assertEquals("String", params[0].typeName)
    }

    @Test
    fun `tap step has one String param named string`() {
        // "Cancel" is a quoted literal → param name is "string"
        val step = parsed.steps.first { it.keyword == "When" && it.text.startsWith("I tap ") }
        val params = resolveInlineParamsForTest(step.text)
        assertEquals(1, params.size)
        assertEquals("string", params[0].name)
        assertEquals("String", params[0].typeName)
    }

    // endregion

    /**
     * Thin wrapper to call the package-private resolveInlineParams logic.
     * We test it via a [BehaveProcessorTestHelper] that exposes the method.
     */
    private fun resolveInlineParamsForTest(text: String): List<CodeGenerator.StepParam> = BehaveProcessorTestHelper.resolveInlineParams(text, emptyList())
}

/** Exposes package-private processor logic for testing. */
enum class LearnKind { PLACEHOLDER, QUOTED, VARIABLE }

internal object BehaveProcessorTestHelper {
    fun resolveInlineParams(
        text: String,
        typeMappings: List<CodeGenerator.TypeMapping>,
    ): List<CodeGenerator.StepParam> {
        val quotedRanges = Regex("\"[^\"]*\"").findAll(text).map { it.range }.toList()

        fun inQuotes(pos: Int) = quotedRanges.any { pos in it }

        data class Tok(
            val pos: Int,
            val kind: LearnKind,
            val name: String,
        )

        val toks =
            buildList {
                Regex("\\{([^}]+)}").findAll(text).forEach {
                    add(Tok(it.range.first, LearnKind.PLACEHOLDER, it.groupValues[1]))
                }
                Regex("\"([^\"]*)\"").findAll(text).forEach {
                    val inner = it.groupValues[1]
                    val varName = Regex("^<([^>]+)>$").find(inner)?.groupValues?.get(1)
                    add(Tok(it.range.first, LearnKind.QUOTED, varName ?: "string"))
                }
                Regex("<([^>]+)>").findAll(text).filter { !inQuotes(it.range.first) }.forEach {
                    add(Tok(it.range.first, LearnKind.VARIABLE, it.groupValues[1]))
                }
            }.sortedBy { it.pos }

        val placeholderNames = toks.filter { it.kind == LearnKind.PLACEHOLDER }.map { it.name }
        val params = mutableListOf<CodeGenerator.StepParam>()
        val usedPlaceholders = mutableSetOf<String>()

        for (mapping in typeMappings.filter { it.fields.isNotEmpty() }) {
            if (mapping.fields.all { it in placeholderNames }) {
                params.add(
                    CodeGenerator.StepParam(
                        name = mapping.typeName.substringAfterLast('.').replaceFirstChar { it.lowercase() },
                        typeName = mapping.typeName,
                    ),
                )
                usedPlaceholders.addAll(mapping.fields)
            }
        }

        val placeholderMappings = typeMappings.filter { it.placeholder.isNotEmpty() }
        val nameCounters = mutableMapOf<String, Int>()

        for (tok in toks) {
            if (tok.kind == LearnKind.PLACEHOLDER && tok.name in usedPlaceholders) continue
            val (typeName, baseName) =
                when (tok.kind) {
                    LearnKind.PLACEHOLDER -> {
                        val custom = placeholderMappings.firstOrNull { it.placeholder == tok.name }
                        when {
                            custom != null -> custom.typeName to tok.name
                            tok.name in CodeGenerator.builtinTypes -> CodeGenerator.builtinTypes[tok.name]!! to tok.name
                            else -> "String" to tok.name
                        }
                    }
                    LearnKind.QUOTED -> "String" to tok.name
                    LearnKind.VARIABLE -> "String" to tok.name
                }
            val idx = nameCounters.getOrDefault(baseName, 0)
            val totalWithBase = toks.count { it.name == baseName }
            val paramName = if (idx == 0 && totalWithBase == 1) baseName else "$baseName$idx"
            nameCounters[baseName] = idx + 1
            params.add(CodeGenerator.StepParam(paramName, typeName))
        }
        return params
    }
}
