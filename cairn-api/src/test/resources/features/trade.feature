Feature: Buying and selling a holding

  Scenario: a purchase weighs the average cost and a sale keeps it
    Given a holding of 500 units at an average cost of 24.12
    When I buy 40 units at 29.10
    Then the holding has 540 units at an average cost of 24.49
    When I sell 140 units
    Then the holding has 400 units at an average cost of 24.49

  Scenario: the first purchase sets a missing average cost
    Given a holding of 120 units without an average cost
    When I buy 20 units at 51.20
    Then the holding has 140 units at an average cost of 51.2

  Scenario: selling everything deletes the holding
    Given a holding of 500 units at an average cost of 24.12
    When I sell 500 units
    Then the sale answers 204
    And the holding no longer exists

  Scenario: selling more than held is refused
    Given a holding of 500 units at an average cost of 24.12
    When I sell 501 units
    Then the sale answers 422
    And the holding has 500 units at an average cost of 24.12
