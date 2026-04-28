Feature: data table null cell support

  Scenario: null text in cell is parsed as null value
    Given the following words
      | word  | translation |
      | Hund  | null        |
      | Katze | cat         |
    Then word count is 2
