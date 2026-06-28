/**
 * ## Example 28: Built-in @Type conversions (no @TypeConverter needed)
 *
 * Demonstrates:
 * - `@Type(SomeEnum::class)` converts case-insensitively via `valueOf` — no converter function.
 * - `@Type(Duration::class)` is parsed by the built-in `kotlin.time.Duration.parse`.
 *
 * Contrast with example 7, which wires a custom `@TypeConverter`. Enums and the handful of
 * built-in types (currently `kotlin.time.Duration`) work out of the box.
 */
package io.mcol.behave.examples.ex28_builtin_types

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.Type
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class WallColor { RED, GREEN, BLUE }

@BehaveFeature("features/28_builtin_types.feature")
class BuiltinTypeSteps : BuiltinTypeStepsSpec {

    private var color: WallColor? = null
    private var timeout: Duration = Duration.ZERO

    override suspend fun iPaintTheWall(@Type(WallColor::class) string: WallColor) {
        color = string
    }

    override suspend fun theWallIsPaintedRed() {
        check(color == WallColor.RED) { "expected RED, was $color" }
    }

    override suspend fun theRequestTimesOutAfter(@Type(Duration::class) string: Duration) {
        timeout = string
    }

    override suspend fun theTimeoutIsAtLeastOneSecond() {
        check(timeout >= 1.seconds) { "expected >= 1s, was $timeout" }
    }
}
