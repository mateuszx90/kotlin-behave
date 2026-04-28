# Example 1: Basic Steps
# Demonstrates: literal steps, Background, Given/When/Then/And/But keywords

Feature: Basic steps

  Background:
    Given the app is launched

  Scenario: Successful login
    Given I am on the login page
    When I enter valid credentials
    And I tap the login button
    Then I see the dashboard
    But I do not see the login form