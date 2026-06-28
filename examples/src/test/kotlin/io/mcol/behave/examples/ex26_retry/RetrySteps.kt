/**
 * ## Example 26: Retrying flaky scenarios — `@Retry`
 *
 * Demonstrates:
 * - `@Retry(times = N)` on a `*Steps` class re-runs a failing scenario up to N extra times.
 * - A scenario passes as soon as one attempt succeeds; a fresh ctx is built per attempt.
 *
 * The flakiness here is deterministic: the attempt counter lives in the companion object so it
 * survives the fresh-instance-per-attempt reset. The first attempt throws; the second passes —
 * so with `@Retry(times = 2)` the generated `RetryGherkinTest` reports the scenario green.
 *
 * Note: tagging a scenario `@flaky` or `@retry` in the feature grants a default retry budget
 * even without this annotation.
 */
package io.mcol.behave.examples.ex26_retry

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.Retry

@Retry(times = 2)
@BehaveFeature("features/26_retry.feature")
class RetrySteps : RetryStepsSpec {

    override suspend fun theFlakyEndpointIsPolled() {
        attempts++
        check(attempts >= ATTEMPTS_NEEDED) { "transient failure on attempt $attempts" }
    }

    override suspend fun theResponseIsSuccessful() {
        check(attempts >= ATTEMPTS_NEEDED)
    }

    companion object {
        // Survives the per-attempt ctx reset so the flakiness is deterministic.
        private var attempts = 0

        // Fail on attempt 1, succeed from attempt 2 onwards.
        private const val ATTEMPTS_NEEDED = 2
    }
}
