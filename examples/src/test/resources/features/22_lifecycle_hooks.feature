# Example 22: Lifecycle hooks — BeforeAll / AfterAll / BeforeStep / AfterStep.
# BeforeAll runs ONCE before the whole feature (not per scenario): both scenarios
# below observe the same single setup.

Feature: Lifecycle hooks

  Scenario: the first scenario sees the suite setup
    Given the suite is initialised
    Then before-all ran exactly once

  Scenario: the second scenario shares the same setup
    Given the suite is initialised
    Then before-all ran exactly once
