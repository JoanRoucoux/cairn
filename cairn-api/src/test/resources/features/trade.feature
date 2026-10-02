Feature: Buying and selling a holding

  Scenario: a purchase weighs the average cost and a sale keeps it
    Given a holding of 500 units at an average cost of 24.12
    When I buy 40 units at 29.10
    Then the holding has 540 units at an average cost of 24.49
    When I sell 140 units
    Then the holding has 400 units at an average cost of 24.49

  Scenario: the first purchase sets a missing average cost
    Given a holding of 342 units without an average cost
    When I buy 20 units at 51.20
    Then the holding has 362 units at an average cost of 51.2

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

  Scenario: a holding moves to another instrument and keeps its quantity and average cost
    Given a holding of 500 units at an average cost of 24.12
    And another instrument
    When I move the holding to the other instrument
    Then the move answers 200
    And the holding is on the other instrument
    And the holding has 500 units at an average cost of 24.12

  Scenario: a holding moved to a quoted instrument comes back valued
    Given a holding of 500 units at an average cost of 24.12
    And another instrument
    And a quote of 30 on the other instrument
    When I move the holding to the other instrument
    Then the move answers 200
    And the moved holding is valued at 15000

  Scenario: a holding cannot move to an instrument its account already holds
    Given a holding of 500 units at an average cost of 24.12
    And another instrument
    And the account already holds the other instrument
    When I move the holding to the other instrument
    Then the move answers 422
    And the holding has 500 units at an average cost of 24.12
