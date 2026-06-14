# Example 16: Recipe Management
# Feature demonstrating common setup steps that will be shared with checkout feature

Feature: Browse Recipes

  Background:
    Given the app is initialized

  Scenario: View recipe list
    Given I skip the onboarding
    When I navigate to recipes
    Then I see the recipe list
    And I see at least 5 recipes

  Scenario: Search recipes by ingredient
    Given I skip the onboarding
    When I navigate to recipes
    And I search for "chicken"
    Then I see recipes containing "chicken"

  Scenario: Filter by cuisine
    Given I skip the onboarding
    When I navigate to recipes
    And I filter by cuisine "Italian"
    Then I see recipes filtered by "Italian"
