# Example 24: the latest Gherkin coverage added to kotlin-behave, exercised end-to-end
# (parsed AND run):
#  - Multiple Examples: blocks under one Scenario Outline — each block keeps its own header+rows.
#  - <placeholder> substitution inside a Doc String AND inside a Data Table, not just step text.
#  - A Doc String content type after the fence (```json) surfaced via params.docStringContentType.
#  - A Rule: carrying a @tag still runs its scenarios (its scenarios inherit the tag — the
#    inheritance assertion lives in GherkinParserTest, the right layer for it).

Feature: Outline substitution, multiple Examples blocks and typed doc strings

  Scenario Outline: the counter reaches <total> after <times> increments
    Given a counter starting at 0
    When I increment it <times> times
    Then the counter is <total>

    Examples: small steps
      | times | total |
      | 1     | 1     |
      | 2     | 2     |

    Examples: a larger step
      | times | total |
      | 5     | 5     |

  Scenario Outline: the payload and config both carry the user <name>
    When I record the payload:
      ```json
      {"user": "<name>"}
      ```
    And I record the config:
      | key  | value  |
      | user | <name> |
    Then the recorded payload contains "<name>"
    And the recorded payload type is "json"
    And the recorded config "user" is "<name>"

    Examples:
      | name  |
      | alice |
      | bob   |

  @checkout
  Rule: tagging a rule still runs its scenarios

    Scenario: a scenario under a tagged rule runs normally
      Given a counter starting at 0
      When I increment it
      Then the counter is 1
