/**
 * ## Example 3: Number Parameters
 *
 * Demonstrates:
 * - Standalone integers (e.g. `10`, `5`) are auto-detected as `{int}` → `Int`
 * - Standalone decimals (e.g. `9.99`) are auto-detected as `{double}` → `Double`
 * - Doubles are matched BEFORE integers to prevent `9.99` → `{int}.99`
 * - Numbers inside quotes (`"10"`) are treated as strings, not numbers
 * - Numbers embedded in words (e.g. `item10`) are NOT extracted
 * - KSP validates concrete values at compile time
 */
package io.mcol.behave.examples.ex03_numbers

import io.mcol.behave.annotations.BehaveFeature
import kotlin.test.assertEquals

@BehaveFeature("features/03_number_parameters.feature")
class NumberSteps : NumberStepsSpec {

    private var stock = 0
    private var price = 0.0
    private var total = 0.0

    override suspend fun givenIHaveItemsInStock(int: Int) {
        stock = int
    }

    override suspend fun whenIAddMoreItems(int: Int) {
        stock += int
    }

    override suspend fun thenIHaveItemsInStock(int: Int) {
        assertEquals(int, stock)
    }

    override suspend fun givenTheItemPriceIs(double: Double) {
        price = double
    }

    override suspend fun whenIBuyItems(int: Int) {
        total = price * int
    }

    override suspend fun thenTheTotalIs(double: Double) {
        assertEquals(double, total, 0.01)
    }
}

