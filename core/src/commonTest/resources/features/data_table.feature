Feature: data table support

  Scenario: step receives all table rows
    Given the following words
      | word  | translation |
      | Hund  | dog         |
      | Katze | cat         |
      | Haus  | house       |
    Then word count is 3
