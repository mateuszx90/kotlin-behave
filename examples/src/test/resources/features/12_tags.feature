# Example 12: Tags
# Demonstrates: @tag annotations on scenarios, used for filtering at runtime

Feature: Tags

  @smoke
  Scenario: Dashboard loads
    Given I am logged in
    Then I see the dashboard

  @smoke @critical
  Scenario: Dashboard shows notifications
    Given I am logged in
    And I have 3 unread notifications
    Then I see the notification badge

  @slow
  Scenario: Dashboard loads full data
    Given I am logged in
    And the database has 10000 records
    Then the dashboard loads within 5 seconds

  @wip
  Scenario: Dashboard shows analytics
    Given I am logged in
    Then I see the analytics widget
