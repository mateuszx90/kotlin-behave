# Example 2: String Parameters
# Demonstrates: quoted "literal" auto-detection as {string}, multiple strings, duplicate naming

Feature: String parameters

  Scenario: Search by keyword
    When I search for "kotlin"
    Then I see results for "kotlin"

  Scenario: Filter results
    When I search for "behave" in category "testing"
    Then the result "behave-ksp" shows "BDD framework"
