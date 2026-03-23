# Example 8: Multi-column Scenario Outline
# Demonstrates: multiple <variables> in Examples, quoted and unquoted,
# mixed with inline text. Each Examples row expands into a full scenario.

Feature: Multi-column scenario outline

  Scenario Outline: User login — <username> as <role>
    Given a user "<username>" with role "<role>"
    When I login as "<username>" with password "<password>"
    Then I am redirected to the "<landing>" page
    And I see a welcome message for "<username>"

    Examples:
      | username | password  | role    | landing   |
      | alice    | pass123   | admin   | dashboard |
      | bob      | secret    | editor  | content   |
      | charlie  | qwerty    | viewer  | home      |

  Scenario Outline: HTTP status codes — <method> <path> returns <status>
    When I send a "<method>" request to "<path>"
    Then the response status is <status>
    And the response contains "<body>"

    Examples:
      | method | path       | status | body           |
      | GET    | /users     | 200    | user list      |
      | POST   | /users     | 201    | created        |
      | DELETE | /users/1   | 204    | deleted         |
      | GET    | /missing   | 404    | not found      |
