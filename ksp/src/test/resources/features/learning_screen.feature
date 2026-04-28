#noinspection CucumberUndefinedStep
Feature: Learning screen

    Background:
        Given I have a collection "Animals" with words:
            | polish | english |
            | pies   | dog     |
            | kot    | cat     |
            | ptak   | bird    |
        And I open the learning screen for that collection

    Scenario: Session starts with the first question
        Then I see the question word "pies"
        And the answer field is empty

    Scenario Outline: Answer feedback — <type>
        When I type "<answer>" in the answer field
        And I submit the answer
        Then I see <type> feedback

        Examples:
            | answer | type         |
            | dog    | correct      |
            | dogg   | semi-correct |
            | doog   | semi-correct |
            | cat    | incorrect    |
            | 1      | incorrect    |

    Scenario: Session completes after all correct answers
        When I answer all words correctly
        Then I see the session summary screen

    Scenario: Back with no progress exits immediately
        When I press back
        Then I am back on the word list screen

    Scenario: Exit confirmation — cancel stays
        When I answer the first word correctly
        And I press back
        Then I see an exit confirmation dialog
        When I tap "Cancel"
        Then I am still on the learning screen

    Scenario: Exit confirmation — discard exits
        When I answer the first word correctly
        And I press back
        And I tap "Discard"
        Then I am back on the word list screen

    Scenario: Check button is disabled before any input
        Then the check button is disabled

    Scenario: Answer field is cleared after submitting and tapping Next
        When I type "dog" in the answer field
        And I submit the answer
        And I tap "Next"
        Then the check button is disabled

    Scenario: Session restart after completion returns to learning screen
        When I answer all words correctly
        And I tap "Restart with same words"
        Then the check button is disabled

    Scenario: Incorrect feedback shows the correct answer
        When I type "xyz" in the answer field
        And I submit the answer
        Then I see incorrect feedback
        And I see the correct answer "dog" in the feedback

    Scenario: Semi-correct feedback shows the correct answer
        When I type "dogg" in the answer field
        And I submit the answer
        Then I see semi-correct feedback
        And I see the correct answer "dog" in the feedback
