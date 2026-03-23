# Example 10: DataTable with @BehaveType
# Demonstrates: typed DataTable rows with composite types and single-column mapping

Feature: Typed data tables

  Scenario: Register pets with details
    Given the following pets:
      | name   | breed    | age |
      | Rex    | Shepherd | 5   |
      | Whisky | Labrador | 3   |
    Then 2 pets are registered
