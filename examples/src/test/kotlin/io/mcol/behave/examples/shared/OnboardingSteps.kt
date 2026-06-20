package io.mcol.behave.examples.shared

import io.mcol.behave.annotations.StepsMixin

/**
 * Shared steps for onboarding used across multiple features.
 *
 * As a `@StepsMixin`, generated `*StepsSpec` interfaces that match these method
 * signatures (e.g., `RecipesSharedStepsSpec`, `CheckoutSharedStepsSpec`) auto-extend
 * this mixin. The implementing `*Steps` class gets the bodies for free — no
 * `by delegate` wiring, no `super<>` overrides.
 */
@StepsMixin
interface OnboardingSteps {
    suspend fun iSkipTheOnboarding() {
        println("✓ Skipping onboarding wizard")
        Thread.sleep(100) // Simulate skip action
    }

    suspend fun theAppIsInitialized() {
        println("✓ App initialized")
    }
}
