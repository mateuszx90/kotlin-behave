# Example 17: Shopping Checkout
# Feature that reuses common "skip onboarding" step from recipes feature

Feature: Shopping Cart & Checkout

  Background:
    Given the app is initialized

  Scenario: Add item to cart
    Given I skip the onboarding
    When I navigate to recipes
    And I add recipe "Margherita Pizza" to cart
    Then the cart has 1 item

  Scenario: Proceed to checkout
    Given I skip the onboarding
    When I navigate to recipes
    And I add recipe "Pasta Carbonara" to cart
    And I open the cart
    And I proceed to checkout
    Then I see the checkout form
    And the total is calculated correctly

  Scenario: Apply discount code
    Given I skip the onboarding
    When I navigate to recipes
    And I add recipe "Caesar Salad" to cart
    And I open the cart
    And I apply discount code "SUMMER2024"
    Then the discount is applied
    And the total is updated
