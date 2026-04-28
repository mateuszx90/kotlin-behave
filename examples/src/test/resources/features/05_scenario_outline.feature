# Example 5: Scenario Outline
# Demonstrates: Scenario Outline with <variable> tokens and Examples table

Feature: Scenario outline

  Scenario Outline: Answer feedback — <type>
    When I type "<answer>" in the answer field
    And I submit the answer
    Then I see <type> feedback

    Examples:
      | answer | type      |
      | dog    | correct   |
      | dogg   | partial   |
      | cat    | incorrect |
