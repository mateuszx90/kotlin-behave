Feature: Counter

  Background:
    Given the counter is 0

  Scenario: increment once
    When I increment it
    Then the counter is 1

  Scenario: increment twice
    When I increment it
    And I increment it
    Then the counter is 2

  Scenario: hooks fire per scenario
    Then the setup hook ran
