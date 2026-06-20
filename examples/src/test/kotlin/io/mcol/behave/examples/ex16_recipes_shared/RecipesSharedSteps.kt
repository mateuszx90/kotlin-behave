/**
 * ## Example 16: Recipes with Shared Steps
 *
 * Demonstrates `@StepsMixin`-based step sharing. The "skipOnboarding()" and
 * "theAppIsInitialized()" steps are NOT implemented here — they come from
 * the [io.mcol.behave.examples.shared.OnboardingSteps] mixin which the generated
 * `RecipesSharedStepsSpec` automatically extends.
 *
 * Result: ZERO code duplication. The implementation is written once in `OnboardingSteps`,
 * and both `RecipesSharedSteps` and `CheckoutSharedSteps` use it via interface inheritance.
 */
package io.mcol.behave.examples.ex16_recipes_shared

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.DivergentStep

data class Recipe(val name: String, val cuisine: String = "unknown")

@BehaveFeature("features/16_recipes_shared.feature")
class RecipesSharedSteps : RecipesSharedStepsSpec {
    // NOTE: skipOnboarding() and theAppIsInitialized() are inherited from the
    // @StepsMixin OnboardingSteps interface via the generated *Spec.
    // iNavigateToRecipes and iSearchFor share names with other features but
    // intentionally diverge per-feature — marked @DivergentStep.

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

    @DivergentStep
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

    @DivergentStep
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
