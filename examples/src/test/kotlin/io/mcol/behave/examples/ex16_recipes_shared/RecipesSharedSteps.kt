/**
 * ## Example 16: Recipes with Shared Steps
 *
 * This example demonstrates trait-based step composition.
 * The "skipOnboarding()" and "theAppIsInitialized()" steps are NOT implemented here.
 * Instead, they are delegated to the OnboardingSteps trait.
 *
 * When you run the test:
 * 1. Gherkin asks: "Given I skip the onboarding"
 * 2. Compiler looks in RecipesSharedSteps — step not found
 * 3. Compiler checks OnboardingSteps (via delegation) — step found and used
 * 4. Test runs: OnboardingSteps.skipOnboarding() is executed
 *
 * Result: ZERO code duplication. The implementation is written once in OnboardingSteps,
 * and both RecipesSharedSteps and CheckoutSharedSteps use it.
 */
package io.mcol.behave.examples.ex16_recipes_shared

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.examples.shared.OnboardingSteps
import io.mcol.behave.examples.shared.OnboardingStepsImpl

data class Recipe(val name: String, val cuisine: String = "unknown")

@BehaveFeature("features/16_recipes_shared.feature")
class RecipesSharedSteps(
    onboarding: OnboardingSteps = OnboardingStepsImpl(),
) : RecipesSharedStepsSpec,
    OnboardingSteps by onboarding {
    // NOTE: skipOnboarding() and theAppIsInitialized() are NOT implemented here!
    // They are delegated to OnboardingSteps via 'by onboarding'

    private val recipes = listOf(
        Recipe("Margherita Pizza", "Italian"),
        Recipe("Pasta Carbonara", "Italian"),
        Recipe("Caesar Salad", "American"),
        Recipe("Thai Green Curry", "Thai"),
        Recipe("Sushi Rolls", "Japanese"),
        Recipe("Chicken Pad Thai", "Thai"),
    )

    private var currentLocation = "home"
    private var searchResults = emptyList<Recipe>()
    private var filterCuisine = ""

    override suspend fun iNavigateToRecipes() {
        println("✓ Navigating to recipes")
        currentLocation = "recipes"
    }

    override suspend fun iSeeTheRecipeList() {
        check(currentLocation == "recipes") { "Not on recipes screen" }
        println("✓ Recipe list displayed")
    }

    override suspend fun iSeeAtLeastRecipes(int: Int) {
        check(recipes.size >= int) { "Expected at least $int recipes, got ${recipes.size}" }
        println("✓ Found ${recipes.size} recipes")
    }

    override suspend fun iSearchFor(string: String) {
        searchResults = recipes.filter { it.name.contains(string, ignoreCase = true) }
        println("✓ Searched for '$string', found ${searchResults.size} recipes")
    }

    override suspend fun iSeeRecipesContaining(string: String) {
        check(searchResults.isNotEmpty()) { "No recipes found for '$string'" }
        check(searchResults.all { it.name.contains(string, ignoreCase = true) }) {
            "Some recipes don't contain '$string'"
        }
        println("✓ All results contain '$string'")
    }

    override suspend fun iFilterByCuisine(string: String) {
        filterCuisine = string
        println("✓ Filtering by cuisine: $string")
    }

    override suspend fun iSeeRecipesFilteredBy(string: String) {
        val filtered = recipes.filter { it.cuisine == string }
        check(filtered.isNotEmpty()) { "No recipes found for cuisine '$string'" }
        println("✓ Showing ${filtered.size} $string recipes")
    }
}
