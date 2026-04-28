# Example 9: DataTable with full type mapping
# Demonstrates:
# - @BehaveType covering ALL columns → List<YourType> directly (no Row class)
# - DataTable combined with inline string parameters
# - Multiple DataTable steps in a single feature
# - Background with DataTable

Feature: DataTable full type mapping

  Background:
    Given the following vocabulary:
      | polish | english |
      | pies   | dog     |
      | kot    | cat     |
      | ptak   | bird    |

  Scenario: All words loaded from background
    Then 3 words are loaded

  Scenario: Add more words and verify count
    When I add the following words:
      | polish  | english |
      | ryba    | fish    |
      | mysz    | mouse   |
    Then 5 words are loaded

  Scenario: Create a collection with words
    Given a collection named "Animals"
    When I assign the following words to "Animals":
      | polish | english |
      | pies   | dog     |
      | kot    | cat     |
    Then the collection "Animals" has 2 words

  Scenario: Import items with numeric columns
    Given the following inventory:
      | product | quantity | price |
      | Apple   | 50       | 1.99  |
      | Bread   | 30       | 3.49  |
      | Milk    | 20       | 2.79  |
    Then the total inventory value is 260.00
