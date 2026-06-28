package io.mcol.behave.gradle

import io.mcol.behave.gherkin.FeatureFileParser
import io.mcol.behave.gherkin.MethodNameGenerator

/**
 * Pure (no IO) lint logic behind the `behaveLint` Gradle task, so it can be unit-tested directly.
 *
 * It parses every feature, surfacing parse errors, and cross-references the step-definition
 * methods declared in source against the method names every feature would generate — reporting
 * any declared step method that no feature references (a "dead" step). Missing steps are already
 * a compile error, so this covers the opposite direction.
 */
object BehaveLint {
    data class FeatureParseError(val file: String, val line: Int, val message: String)

    data class Result(
        val parseErrors: List<FeatureParseError>,
        val deadSteps: List<String>,
    ) {
        val hasParseErrors: Boolean get() = parseErrors.isNotEmpty()
    }

    // Override methods that are lifecycle/runner hooks, not feature steps — never "dead".
    private val lifecycleDenylist = setOf(
        "beforeScenario",
        "afterScenario",
        "runScenario",
    )

    private val overrideStepFun = Regex("""override\s+suspend\s+fun\s+(\w+)\s*\(""")

    /**
     * @param features map of feature file path → its raw text.
     * @param stepSources the text of every step-definition source file.
     */
    fun analyze(features: Map<String, String>, stepSources: List<String>): Result {
        val parseErrors = mutableListOf<FeatureParseError>()
        val referenced = mutableSetOf<String>()
        for ((path, text) in features) {
            val parsed = FeatureFileParser.parse(text)
            parsed.errors.forEach { parseErrors.add(FeatureParseError(path, it.line, it.message)) }
            if (!parsed.hasErrors) {
                val pairs = parsed.steps.map { it.keyword to it.text }
                referenced.addAll(MethodNameGenerator.resolveCollisions(pairs))
            }
        }
        val declared = stepSources.flatMap(::declaredStepMethods).toSet()
        val dead = (declared - referenced - lifecycleDenylist).sorted()
        return Result(parseErrors, dead)
    }

    /** Step-definition method names declared as `override suspend fun name(` in a source file. */
    fun declaredStepMethods(source: String): List<String> = overrideStepFun.findAll(source).map { it.groupValues[1] }.toList()
}
