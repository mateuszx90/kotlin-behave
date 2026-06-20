Feature: Web search
  Shares "the user is logged in" with 19b_mobile_search.feature, but the
  implementation differs (web uses a session cookie, mobile uses a debug auto-login).
  The divergence is opt-in via @DivergentStep on both overrides.

  Scenario: Search via the URL bar
    Given the user is logged in
    When I navigate to "/search?q=kotlin"
    Then the URL contains "kotlin"
