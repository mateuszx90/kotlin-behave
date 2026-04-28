Feature: Outline type validation

    Scenario Outline: Click button multiple times
        When I click <count> times
        Examples:
            | count |
            | 3     |
            | 5     |

    Scenario Outline: Mixed number types
        When I set temperature to <value>
        Examples:
            | value |
            | 4     |
            | 5.5   |
