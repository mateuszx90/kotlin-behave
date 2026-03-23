# Example 13: Hooks — Before/After
# Demonstrates: lifecycle hooks for setup/teardown around each scenario

Feature: Hooks

  Scenario: Insert a record
    When I insert a user "Alice"
    Then the user "Alice" exists in the database

  Scenario: Delete a record
    When I insert a user "Bob"
    And I delete the user "Bob"
    Then the user "Bob" does not exist in the database
