Feature: Number handling

    Scenario: Create recipe with 4 portions
        When I create a recipe with 4 portions named "Pasta"

    Scenario: Create recipe with 2 portions
        When I create a recipe with 2 portions named "Salad"

    Scenario: Temperature check
        Then the temperature is 36.6

    Scenario: Negative value
        Then I have -3 items
