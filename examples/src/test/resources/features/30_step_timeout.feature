Feature: Per-step timeout

  Each step must finish within the per-step budget passed to gherkin(...).
  These steps are fast, so the scenario passes well inside the 2s limit.

  Scenario: fast steps complete within the per-step budget
    Given a quick operation
    Then it finished in time
