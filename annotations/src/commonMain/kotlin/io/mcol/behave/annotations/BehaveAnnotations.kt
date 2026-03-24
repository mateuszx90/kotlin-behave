package io.mcol.behave.annotations

import kotlin.reflect.KClass

/**
 * Marks a class as a Gherkin step definitions implementation for the given feature file.
 * KSP will generate an interface [ClassName]Spec with one method per unique step.
 *
 * @param path Path to the .feature file, relative to [behave.featureDir] KSP option
 *             (default: "src/commonTest/resources").
 * @param generateTest When true (default), KSP also generates a `val generatedXxxSteps`
 *                     instance and a Kotest `FreeSpec` test class. Set to false when you
 *                     need custom wiring (e.g. `parameterType()` registration, hooks, or
 *                     tag filtering).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class BehaveFeature(
    val path: String,
    val generateTest: Boolean = true,
)

/**
 * Configures type mapping for placeholders or DataTable columns in steps.
 *
 * Three modes:
 * - **Placeholder**: `placeholder` set, `fields` empty — maps `{placeholder}` to [type]
 * - **Field auto-detect**: `placeholder` empty, `fields` empty — absorbs all step tokens
 *   matching [type]'s primary constructor parameter names
 * - **Field explicit**: `placeholder` empty, `fields` non-empty — same as auto-detect but
 *   with explicit field names provided
 *
 * Repeatable — apply multiple times for multiple type mappings.
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class BehaveType(
    val placeholder: String = "",
    val type: KClass<*>,
    val fields: Array<String> = [],
)

/**
 * Marks a parameter for lossy type casting in generated step definitions.
 *
 * When a step has `{int}` but concrete values include decimals (e.g., `5.5`),
 * annotating the parameter with `@BehaveCast` suppresses the compile-time type
 * error and generates conversion code that truncates the value.
 *
 * @param lossy When true, allows truncating conversions (e.g., Double → Int).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class BehaveCast(val lossy: Boolean = true)
