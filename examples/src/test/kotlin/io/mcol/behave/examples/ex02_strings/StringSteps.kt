/**
 * ## Example 2: String Parameters
 *
 * Demonstrates:
 * - Quoted `"literal"` values in feature text are auto-detected as `{string}` parameters
 * - Single string param → named `string`
 * - Multiple string params → indexed: `string0`, `string1`
 * - Method name strips quoted content: `"I search for X"` → `whenISearchFor`
 */
package io.mcol.behave.examples.ex02_strings

import io.mcol.behave.annotations.BehaveFeature

@BehaveFeature("features/02_string_parameters.feature")
class StringSteps : StringStepsSpec {

    private val results = mutableMapOf<String, List<String>>()

    override suspend fun whenISearchFor(string: String) {
        results[string] = listOf("$string-result")
    }

    override suspend fun thenISeeResultsFor(string: String) {
        check(results.containsKey(string)) { "No results for '$string'" }
    }

    // Two quoted strings → string0 and string1
    override suspend fun whenISearchForInCategory(string0: String, string1: String) {
        results[string0] = listOf("$string0 in $string1")
    }

    override suspend fun thenTheResultShows(string0: String, string1: String) {
        check(results.isNotEmpty()) { "No results to check" }
    }
}

val generatedStringSteps = StringStepsSpec.steps { StringSteps() }
