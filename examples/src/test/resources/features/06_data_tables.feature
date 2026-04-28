# Example 6: Data Tables
# Demonstrates: steps with | column | DataTable, auto-generated Row class

Feature: Data tables

  Scenario: Import users from a table
    Given the following users:
      | name    | email             | age |
      | Alice   | alice@example.com | 30  |
      | Bob     | bob@example.com   | 25  |
      | Charlie | charlie@test.com  | 35  |
    Then 3 users are registered
