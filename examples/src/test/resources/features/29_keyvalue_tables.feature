Feature: Key-value tables and table diffing

  Scenario: build an object from a vertical key-value table
    Given a user
      | name  | Alice             |
      | email | alice@example.com |
      | age   | 30                |
    Then the user is named "Alice" aged 30

  Scenario: assert an actual table matches the expected one
    Given the expected roster
      | name  | age |
      | Alice | 30  |
      | Bob   | 25  |
    Then the actual roster matches the expected table
