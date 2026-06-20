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
 * Marks an interface as a reusable step mixin.
 *
 * When KSP processes a `@BehaveFeature` class, any step whose generated method signature
 * (name + parameter types) matches a method declared in a `@StepsMixin` interface is treated
 * as **inherited**: the generated `*StepsSpec` extends the mixin instead of redeclaring
 * the method as abstract. The implementing class only needs to satisfy the mixin's
 * abstract members (typically `val app: AppRobot`).
 *
 * Use mixins to share step bodies across feature files without forcing every `*Steps`
 * class to override the same method.
 *
 * Example:
 * ```
 * @StepsMixin
 * interface NavigationStepsMixin {
 *     val app: AppRobot
 *     suspend fun iNavigateToSettings() = app.navigateToSettings()
 * }
 * ```
 *
 * The interface must live in the same KSP compilation unit (typically `commonTest`) as
 * the `@BehaveFeature` classes that consume it.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class StepsMixin

/**
 * Marks a step override that is **intentionally divergent** from a same-named step
 * in another feature file.
 *
 * The processor errors when the same Gherkin step text (and therefore the same generated
 * method signature) appears in 2+ feature files and is NOT covered by a `@StepsMixin`.
 * Two ways to resolve the error:
 *
 *  - Extract the shared body into a `@StepsMixin` interface, so it's written once, OR
 *  - Mark the override with `@DivergentStep` in EVERY `*Steps` class that diverges,
 *    declaring that the divergence is deliberate.
 *
 * The annotation must appear on the override in every diverging class — missing one
 * still errors. Use a mixin instead when the bodies could be the same.
 *
 * Example:
 * ```
 * class WebSearchSteps : WebSearchStepsSpec {
 *     @DivergentStep
 *     override suspend fun iAmLoggedIn() { /* seed cookie store */ }
 * }
 *
 * class MobileSearchSteps : MobileSearchStepsSpec {
 *     @DivergentStep
 *     override suspend fun iAmLoggedIn() { /* call debug-menu auto-login */ }
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class DivergentStep

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
annotation class BehaveCast(
    val lossy: Boolean = true,
)
