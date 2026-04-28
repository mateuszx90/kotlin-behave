# Example 4: All Built-in Parameter Types
# Shows how various value types are handled by KSP auto-detection:
#   "quoted text"  → {string} → String
#   25             → {int}    → Int
#   98.5           → {double} → Double
# For types that can't be auto-detected (boolean, long, word),
# use Scenario Outline with <variable> so concrete values appear in Examples.

Feature: All parameter types

  Scenario: String and number types
    Given the form field "name" has value "Alice"
    When I set the age to 25
    And I set the score to 98.5
    Then the form is valid

  Scenario Outline: Boolean flag
    When I set the premium flag to <flag>
    Then the form is valid

    Examples:
      | flag  |
      | true  |
      | false |
