# Example 3: Number Parameters
# Demonstrates: standalone integers and doubles are auto-detected from step text

Feature: Number parameters

  Scenario: Add items to stock
    Given I have 10 items in stock
    When I add 5 more items
    Then I have 15 items in stock

  Scenario: Price calculation
    Given the item price is 9.99
    When I buy 3 items
    Then the total is 29.97
