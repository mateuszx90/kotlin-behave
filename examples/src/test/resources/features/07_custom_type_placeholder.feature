# Example 7: Custom Type — Placeholder Mode
# Demonstrates: @BehaveType(placeholder=...) mapping {token} to a domain type

Feature: Custom type placeholder

  Scenario: Navigate via buttons
    When I tap the {label} button
    Then I see the {screen} screen
