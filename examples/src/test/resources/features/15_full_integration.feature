# Example 15: Full Integration
# Complete end-to-end: Background, literal steps, string params, numbers,
# Scenario Outline, DataTable, tags — all in one feature.

Feature: Todo list

  Background:
    Given the todo list is empty

  Scenario: Add a todo item
    When I add a todo "Buy groceries"
    Then the todo "Buy groceries" is displayed
    And 1 todo is displayed

  Scenario: Complete a todo
    Given I have a todo "Write tests"
    When I complete the todo "Write tests"
    Then the todo "Write tests" is marked as done

  Scenario Outline: Priority levels
    When I add a todo "Task" with priority "<priority>"
    Then the last todo shows "<priority>" priority

    Examples:
      | priority |
      | high     |
      | medium   |
      | low      |

  Scenario: Batch import todos
    When I import the following todos:
      | title        | priority |
      | Clean house  | high     |
      | Read book    | low      |
      | Call dentist | medium   |
    Then 3 todos are displayed

  @smoke
  Scenario: Empty state
    Then 0 todos are displayed
