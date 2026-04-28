# Example 11: @BehaveCast — Lossy Conversion
# Demonstrates: step auto-detects as {int} but a scenario uses a decimal value.
# Without @BehaveCast, KSP reports a type mismatch compile error.
# With @BehaveCast, the step expression is widened {int} → {double} and .toInt() is generated.

Feature: BehaveCast

  Scenario: Create recipe with whole portions
    When I create a recipe with 4 portions
    Then the recipe has 4 portions

  Scenario: Create recipe with decimal portions
    When I create a recipe with 2.5 portions
    Then the recipe has 2.5 portions
