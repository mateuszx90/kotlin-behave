Feature: Profile screen
  Same SessionMixin steps as 18a_settings, plus uses both methods of NavigationMixin:
  "I am on the home screen" as a precondition AND "I go back" inside a scenario.
  ProfileSteps only implements the profile-specific steps.

  Scenario: View profile after logging in
    Given the app is started
    And I am on the home screen
    And I log in as "bob"
    When I open my profile
    Then I see the user name "bob"

  Scenario: Going back from the profile returns to the home screen
    Given the app is started
    And I am on the home screen
    And I log in as "carol"
    And I open my profile
    When I go back
    Then I see the current screen "home"
