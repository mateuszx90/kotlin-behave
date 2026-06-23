# Example 23: Gherkin keyword coverage added to kotlin-behave — the '*' step bullet,
# the Example:/Scenarios:/Scenario Template: synonyms, Rule: grouping (with its own
# Background), and table cells that escape a pipe (\|) or are left empty.

Feature: Gherkin keyword coverage

  Rule: asterisk bullet and the Example synonym

    Background:
      Given a counter starting at 0

    Example: asterisk steps inherit the previous keyword
      When I increment it
      * I increment it
      Then the counter is 2

  Scenario Template: Scenarios is a synonym for Examples
    Given a counter starting at 0
    When I increment it <times> times
    Then the counter is <total>

    Scenarios:
      | times | total |
      | 1     | 1     |
      | 4     | 4     |

  Scenario: table cells with an escaped pipe and an empty value
    Given the following config:
      | key   | value |
      | regex | a\|b  |
      | empty |       |
    Then config "regex" is "a|b"
    And config "empty" is empty
