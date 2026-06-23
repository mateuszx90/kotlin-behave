# Example 21: Doc Strings — multi-line text attached to a step.
# The fenced block (""" or ```) after a step becomes a trailing `docString: String`
# parameter on the generated method. Indentation is stripped relative to the fence;
# blank lines and '#' inside the block are preserved verbatim.

Feature: Doc strings

  Scenario: A multi-line payload is passed to the step
    When I send the payload:
      ```
      line one
      line two
      ```
    Then the received payload is:
      ```
      line one
      line two
      ```
