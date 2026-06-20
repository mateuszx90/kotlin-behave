Feature: Mobile search
  Shares "the user is logged in" with 19a_web_search.feature. Each platform's
  step body legitimately differs — both marked @DivergentStep.

  Scenario: Search via the tab bar
    Given the user is logged in
    When I tap the search tab
    And I type "kotlin" in the search field
    Then the suggestions list shows "kotlin"
