Feature: Word list adapter

  Background:
    Given the adapter count is 0

  Scenario Outline: section counts
    Given adapter count is <count>
    Then currently learning count is <cl>

    Examples:
      | count | cl |
      | 5     | 5  |
      | 10    | 10 |
      | 11    | 10 |
