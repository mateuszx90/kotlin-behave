package io.mcol.behave.examples.shared

/**
 * Shared interface for onboarding steps used across multiple features.
 * This interface is implemented once and delegated by multiple feature tests.
 */
interface OnboardingSteps {
    suspend fun iSkipTheOnboarding() {
        println("✓ Skipping onboarding wizard")
        Thread.sleep(100) // Simulate skip action
    }

    suspend fun theAppIsInitialized() {
        println("✓ App initialized")
    }
}
