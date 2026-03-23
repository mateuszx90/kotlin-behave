/**
 * ## Example 15: Full Integration
 *
 * Complete end-to-end example demonstrating the entire kotlin-behave workflow:
 *
 * 1. Write `.feature` file with Gherkin scenarios
 * 2. Annotate class with `@BehaveFeature("path/to/feature.feature")`
 * 3. Build → KSP generates `*Spec` interface + companion `steps()` factory
 * 4. Implement the `*Spec` interface — compiler tells you what's missing
 * 5. Create steps instance: `val steps = XxxStepsSpec.steps { XxxSteps() }`
 * 6. Wire to Kotest: `gherkin("path/to/feature.feature", steps)`
 * 7. Run tests — each scenario is a separate test case
 *
 * This example combines: Background, literal steps, string params, numbers,
 * Scenario Outline, DataTable, and tags — all in one feature.
 */
package io.mcol.behave.examples.ex15_integration

import io.mcol.behave.annotations.BehaveFeature
import kotlin.test.assertEquals

data class Todo(val title: String, val priority: String = "medium", var done: Boolean = false)

@BehaveFeature("features/15_full_integration.feature")
class IntegrationSteps : IntegrationStepsSpec {

    private val todos = mutableListOf<Todo>()

    // Background
    override suspend fun givenTheTodoListIsEmpty() {
        todos.clear()
    }

    // String param from quoted literal
    override suspend fun whenIAddATodo(string: String) {
        todos.add(Todo(title = string))
    }

    override suspend fun thenTheTodoIsDisplayed(string: String) {
        check(todos.any { it.title == string }) { "Todo '$string' not found" }
    }

    // Auto-detected number from "1 todo"
    override suspend fun andTodoIsDisplayed(int: Int) {
        assertEquals(int, todos.size)
    }

    override suspend fun givenIHaveATodo(string: String) {
        todos.add(Todo(title = string))
    }

    override suspend fun whenICompleteTheTodo(string: String) {
        todos.first { it.title == string }.done = true
    }

    override suspend fun thenTheTodoIsMarkedAsDone(string: String) {
        check(todos.first { it.title == string }.done)
    }

    // Scenario Outline: <priority> → {word}
    override suspend fun whenIAddATodoWithPriority(string: String, priority: String) {
        todos.add(Todo(title = string, priority = priority))
    }

    override suspend fun thenTheLastTodoShowsPriority(priority: String) {
        assertEquals(priority, todos.last().priority)
    }

    // DataTable with auto-generated Row class
    override suspend fun whenIImportTheFollowingTodos(rows: List<WhenIImportTheFollowingTodosRow>) {
        rows.forEach { row ->
            todos.add(Todo(title = row.title, priority = row.priority))
        }
    }

    override suspend fun thenTodosAreDisplayed(int: Int) {
        assertEquals(int, todos.size)
    }
}

val generatedIntegrationSteps = IntegrationStepsSpec.steps { IntegrationSteps() }
