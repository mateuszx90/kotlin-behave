Feature: Settings screen
  Two shared steps from SessionMixin ("the app is started", "I log in as ...")
  plus one shared step from NavigationMixin ("I am on the home screen") — none of
  these are declared in SettingsSteps. Only the theme-specific steps are.

  Scenario: Change the theme after logging in from the home screen
    Given the app is started
    And I am on the home screen
    And I log in as "alice"
    When I change the theme to "dark"
    Then the theme is "dark"
