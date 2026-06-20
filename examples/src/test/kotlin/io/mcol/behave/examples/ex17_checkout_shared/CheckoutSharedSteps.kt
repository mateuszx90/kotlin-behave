/**
 * ## Example 17: Checkout with Shared Steps
 *
 * Demonstrates reusing the [io.mcol.behave.examples.shared.OnboardingSteps] mixin
 * across multiple features.
 *
 * Both `RecipesSharedSteps` and `CheckoutSharedSteps` consume the SAME mixin via the
 * generated `*Spec` extending it — no constructor injection, no `by delegate`, no
 * reimplementation here.
 */
package io.mcol.behave.examples.ex17_checkout_shared

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.DivergentStep

data class CartItem(val recipe: String, val price: Double = 9.99)

@BehaveFeature("features/17_checkout_shared.feature")
class CheckoutSharedSteps : CheckoutSharedStepsSpec {
    // NOTE: skipOnboarding() and theAppIsInitialized() are inherited from the
    // @StepsMixin OnboardingSteps interface via the generated *Spec.
    // iNavigateToRecipes shares its name with another feature but its body is
    // intentionally checkout-specific — marked @DivergentStep.

    private val cart = mutableListOf<CartItem>()
    private var currentLocation = "home"
    private var discountApplied = false
    private var discountPercent = 0

    @DivergentStep
    override suspend fun iNavigateToRecipes() {
        println("✓ Navigating to recipes")
        currentLocation = "recipes"
    }

    override suspend fun iAddRecipeToCart(recipeName: String) {
        cart.add(CartItem(recipeName))
        println("✓ Added '$recipeName' to cart (${cart.size} items)")
    }

    override suspend fun theCartHasItem(int: Int) {
        check(cart.size == int) { "Expected $int items, got ${cart.size}" }
        println("✓ Cart has $int item(s)")
    }

    override suspend fun iOpenTheCart() {
        println("✓ Opened cart with ${cart.size} items")
        currentLocation = "cart"
    }

    override suspend fun iProceedToCheckout() {
        check(cart.isNotEmpty()) { "Cannot checkout with empty cart" }
        println("✓ Proceeding to checkout with ${cart.size} items")
        currentLocation = "checkout"
    }

    override suspend fun iSeeTheCheckoutForm() {
        check(currentLocation == "checkout") { "Not on checkout screen" }
        println("✓ Checkout form displayed")
    }

    override suspend fun theTotalIsCalculatedCorrectly() {
        val subtotal = cart.sumOf { it.price }
        val discount = if (discountApplied) subtotal * discountPercent / 100 else 0.0
        val total = subtotal - discount
        check(total > 0) { "Invalid total: $total" }
        println("✓ Total calculated: $${"%.2f".format(total)}")
    }

    override suspend fun iApplyDiscountCode(code: String) {
        when (code) {
            "SUMMER2024" -> {
                discountApplied = true
                discountPercent = 10
                println("✓ Discount code '$code' applied (10% off)")
            }
            else -> throw IllegalArgumentException("Invalid discount code: $code")
        }
    }

    override suspend fun theDiscountIsApplied() {
        check(discountApplied) { "Discount not applied" }
        println("✓ Discount applied: $discountPercent%")
    }

    override suspend fun theTotalIsUpdated() {
        val subtotal = cart.sumOf { it.price }
        val discount = subtotal * discountPercent / 100
        val total = subtotal - discount
        println("✓ Total updated: \$${("%.2f".format(total))}")
    }
}
