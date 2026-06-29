package io.mcol.behave.types

import kotlin.time.Duration

/**
 * Shared value-validation rules, used by BOTH sides so they can never disagree:
 *  - the KSP processor calls the `*Problem` functions to predict a failure at **build time**
 *    (returning a message, or null when the value is fine), and
 *  - the generated runtime conversions call `toEnum` / `toDuration` to **fail fast with the same
 *    rule** (and the same wording) instead of a raw `valueOf` / `parse` exception.
 *
 * This matters whenever compile-time can't cover a value: a `.feature` edited after the build
 * (features are re-read at runtime) or a dynamically-registered type.
 */
public object ValueValidation {

    // region enums

    /** Build-time check: null when [value] names a constant of the enum (case-insensitive). */
    public fun enumProblem(value: String, enumName: String, constants: Collection<String>): String? {
        if (constants.any { it.equals(value, ignoreCase = true) }) return null
        return "'$value' is not a constant of $enumName (expected one of ${constants.sorted().joinToString()})"
    }

    /**
     * Runtime conversion: resolve [value] to one of [constants] case-insensitively, or throw the
     * same message. Non-inline (the generated code passes `SomeEnum.values()`) so it imposes no
     * JVM-target floor on consumers.
     */
    public fun <T : Enum<T>> toEnum(value: String, constants: Array<T>, enumName: String): T {
        val match = constants.firstOrNull { it.name.equals(value, ignoreCase = true) }
        if (match != null) return match
        val names = constants.map { it.name }
        throw IllegalArgumentException("Invalid enum value: ${enumProblem(value, enumName, names) ?: "'$value' is invalid"}")
    }

    // endregion

    // region Duration

    /** Build-time check: null when [value] parses as a `kotlin.time.Duration`. */
    public fun durationProblem(value: String): String? = try {
        Duration.parse(value)
        null
    } catch (_: IllegalArgumentException) {
        "'$value' is not a valid Duration (expected e.g. \"1500ms\", \"2s\", \"1.5h\")"
    }

    /** Runtime conversion: parse [value] as a `Duration`, or throw the same message. */
    public fun toDuration(value: String): Duration = try {
        Duration.parse(value)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid Duration literal: ${durationProblem(value) ?: "'$value' is invalid"}")
    }

    // endregion

    // region numeric range

    /**
     * Build-time check: null when [value] fits the Kotlin type. The placeholder regex (`-?\d+`)
     * accepts any run of digits, but the runtime parses via `toInt()`/`toLong()`, which overflow
     * for out-of-range values. Accepts both the Kotlin name ("Int") and placeholder name ("int").
     */
    public fun numericRangeProblem(value: String, kotlinType: String): String? = when (kotlinType) {
        "Int", "int" -> if (value.toIntOrNull() == null) "'$value' does not fit in Int" else null
        "Long", "long" -> if (value.toLongOrNull() == null) "'$value' does not fit in Long" else null
        else -> null
    }

    // endregion
}
