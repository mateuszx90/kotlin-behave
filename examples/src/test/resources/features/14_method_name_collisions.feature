# Example 14: Method Name Collisions
# Steps "I have X items in the cart" and "I have X items in my wishlist"
# produce different method names, so no collision here.
# True collisions get numeric suffixes (e.g. thenIHaveItems0, thenIHaveItems1).

Feature: Method name collisions

  Scenario: Items by count
    Given I have 3 items in the cart
    And I have 2 items in my wishlist
    Then I have 5 items total
