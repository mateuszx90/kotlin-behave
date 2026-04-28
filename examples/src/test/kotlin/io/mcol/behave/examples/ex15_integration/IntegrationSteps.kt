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
    override suspend fun theTodoListIsEmpty() {
        todos.clear()
    }

    // String param from quoted literal
    override suspend fun iAddATodo(string: String) {
        todos.add(Todo(title = string))
    }

    override suspend fun theTodoIsDisplayed(string: String) {
        check(todos.any { it.title == string }) { "Todo '$string' not found" }
    }

    // Auto-detected number from "1 todo"
    override suspend fun todoIsDisplayed(int: Int) {
        assertEquals(int, todos.size)
    }

    override suspend fun iHaveATodo(string: String) {
        todos.add(Todo(title = string))
    }

    override suspend fun iCompleteTheTodo(string: String) {
        todos.first { it.title == string }.done = true
    }

    override suspend fun theTodoIsMarkedAsDone(string: String) {
        check(todos.first { it.title == string }.done)
    }

    // Scenario Outline: <priority> → {word}
    override suspend fun iAddATodoWithPriority(string: String, priority: String) {
        todos.add(Todo(title = string, priority = priority))
    }

    override suspend fun theLastTodoShowsPriority(priority: String) {
        assertEquals(priority, todos.last().priority)
    }

    // DataTable with auto-generated Row class
    override suspend fun iImportTheFollowingTodos(rows: List<IImportTheFollowingTodosRow>) {
        rows.forEach { row ->
            todos.add(Todo(title = row.title, priority = row.priority))
        }
    }

    override suspend fun todosAreDisplayed(int: Int) {
        assertEquals(int, todos.size)
    }
}

// KSP generates generatedIntegrationSteps and IntegrationGherkinTest automatically.
