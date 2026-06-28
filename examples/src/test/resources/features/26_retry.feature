Feature: Retrying flaky scenarios

  A scenario that fails on its first attempt but passes on a later one should be
  reported green when the steps class opts in with @Retry.

  Scenario: A flaky endpoint eventually responds
    When the flaky endpoint is polled
    Then the response is successful
