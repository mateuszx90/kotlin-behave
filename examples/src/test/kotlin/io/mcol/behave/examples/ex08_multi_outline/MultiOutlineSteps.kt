/**
 * ## Example 8: Multi-column Scenario Outline
 *
 * Demonstrates:
 * - Multiple `<variable>` tokens from a single Examples table
 * - Quoted `"<variable>"` → `{string}` (matched with quotes at runtime)
 * - Unquoted `<variable>` → `{word}` (matched as non-whitespace)
 * - 4-column Examples table: each row produces a full scenario
 * - Parameter names come from variable names, not generic `string0`/`string1`
 * - Two separate Scenario Outlines in one feature, each with its own Examples
 *
 * Key insight: when a variable appears both quoted (`"<x>"`) and unquoted (`<x>`)
 * across sibling templates, KSP unifies to `{string}`. Keep quoting consistent
 * within a step to avoid surprises.
 */
package io.mcol.behave.examples.ex08_multi_outline

import io.mcol.behave.annotations.BehaveFeature
import kotlin.test.assertEquals

@BehaveFeature("features/08_multi_column_outline.feature")
class MultiOutlineSteps : MultiOutlineStepsSpec {

    // --- User login outline ---

    private var currentRole = ""
    private var loggedInUser = ""
    private var landingPage = ""

    override suspend fun givenAUserWithRole(username: String, role: String) {
        currentRole = role
    }

    override suspend fun whenILoginAsWithPassword(username: String, password: String) {
        loggedInUser = username
        landingPage = when (currentRole) {
            "admin" -> "dashboard"
            "editor" -> "content"
            else -> "home"
        }
    }

    override suspend fun thenIAmRedirectedToThePage(landing: String) {
        assertEquals(landing, landingPage)
    }

    override suspend fun andISeeAWelcomeMessageFor(username: String) {
        assertEquals(loggedInUser, username)
    }

    // --- HTTP status codes outline ---

    private var responseStatus = ""
    private var responseBody = ""

    override suspend fun whenISendARequestTo(method: String, path: String) {
        // Simulate HTTP responses based on method + path
        when {
            method == "GET" && path == "/users" -> { responseStatus = "200"; responseBody = "user list" }
            method == "POST" && path == "/users" -> { responseStatus = "201"; responseBody = "created" }
            method == "DELETE" && path == "/users/1" -> { responseStatus = "204"; responseBody = "deleted" }
            else -> { responseStatus = "404"; responseBody = "not found" }
        }
    }

    // <status> is unquoted → {word} → String (not Int, even though values are numeric)
    override suspend fun thenTheResponseStatusIs(status: String) {
        assertEquals(status, responseStatus)
    }

    override suspend fun andTheResponseContains(body: String) {
        assertEquals(body, responseBody)
    }
}

val generatedMultiOutlineSteps = MultiOutlineStepsSpec.steps { MultiOutlineSteps() }
