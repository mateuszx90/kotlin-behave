/**
 * Mobile counterpart to [WebSearchSteps]. Both classes share the step "the user is
 * logged in" but with intentionally different bodies — each marked `@DivergentStep`.
 */
package io.mcol.behave.examples.ex19_divergent_steps

import io.mcol.behave.annotations.BehaveFeature
import io.mcol.behave.annotations.DivergentStep

@BehaveFeature("features/19b_mobile_search.feature")
class MobileSearchSteps : MobileSearchStepsSpec {

    private var debugLoginInvoked: Boolean = false
    private var currentScreen: String = "home"
    private var searchQuery: String = ""
    private val suggestions: MutableList<String> = mutableListOf()

    @DivergentStep
    override suspend fun theUserIsLoggedIn() {
        debugLoginInvoked = true
    }

    override suspend fun iTapTheSearchTab() {
        check(debugLoginInvoked) { "Not logged in" }
        currentScreen = "search"
    }

    override suspend fun iTypeInTheSearchField(string: String) {
        check(currentScreen == "search") { "Not on search screen" }
        searchQuery = string
        suggestions.add(string)
    }

    override suspend fun theSuggestionsListShows(string: String) {
        check(string in suggestions) {
            "Expected suggestions to contain '$string' but were $suggestions"
        }
    }
}
