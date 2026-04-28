/**
 * ## Example 14: Method Name Collisions
 *
 * Demonstrates:
 * - When two steps produce the same camelCase method name after stripping
 *   placeholders/numbers, KSP adds numeric suffixes to disambiguate.
 * - Example: "I have {int} items in the cart" and "I have {int} items in my wishlist"
 *   both strip to different names so no collision here.
 * - Collision resolution is deterministic — same feature file always produces same names.
 */
package io.mcol.behave.examples.ex14_collisions

import io.mcol.behave.annotations.BehaveFeature
import kotlin.test.assertEquals

@BehaveFeature("features/14_method_name_collisions.feature")
class CollisionSteps : CollisionStepsSpec {

    private var cartItems = 0
    private var wishlistItems = 0

    override suspend fun iHaveItemsInTheCart(int: Int) {
        cartItems = int
    }

    override suspend fun iHaveItemsInMyWishlist(int: Int) {
        wishlistItems = int
    }

    override suspend fun iHaveItemsTotal(int: Int) {
        assertEquals(int, cartItems + wishlistItems)
    }
}
