# Example 20: Scenario Outline parameter type inference
#
# Each Examples column whose values are ALL one numeric/boolean type is inferred to that
# Kotlin type (Int / Long / Double / Boolean); a column with mixed values stays String.
# A step that appears BOTH in an Examples table and as a standalone literal unifies to the
# same type — the type is decided from every concrete instance, not just the first one.

Feature: Outline parameter type inference

  # One table, five columns, five different inferred types.
  Scenario Outline: each typed column resolves to its own Kotlin type
    When I record count <count> ratio <ratio> enabled <enabled> big <big> label <label>
    Then the recorded count is <count>

    Examples:
      | count | ratio | enabled | big         | label |
      | 1     | 1.5   | true    | 10000000000 | alpha |
      | 2     | 2.25  | false   | 20000000000 | beta  |
      | 3     | 3.0   | true    | 30000000000 | gamma |

  # The same step "I tick <n> times" appears here as a numeric outline column...
  Scenario Outline: tick count from an Examples table
    When I tick <n> times
    Then the ticked total is <n>

    Examples:
      | n |
      | 1 |
      | 2 |
      | 3 |

  # ...and here as a standalone literal. Both must unify to Int.
  Scenario: tick count from a standalone literal
    When I tick 5 times
    Then the ticked total is 5

  # A column with mixed value kinds cannot be one type, so it stays String.
  Scenario Outline: a mixed-value column stays a String
    When I submit code <code>
    Then the submitted code is "<code>"

    Examples:
      | code |
      | 1    |
      | abc  |
      | 3    |
