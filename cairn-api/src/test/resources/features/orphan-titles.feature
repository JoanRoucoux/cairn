Feature: A title leaves the catalogue with its last line

  Scenario: deleting the last line removes the title and its quotes
    Given Alex holds 5 units of the Yahoo title "NWI.PA" in the first account
    And the title "NWI.PA" has a recorded quote of 21.50
    When Alex deletes the line of the title "NWI.PA" in the first account
    Then the title "NWI.PA" is no longer tracked
    And the title "NWI.PA" has 0 recorded quotes

  Scenario: selling everything removes the title and its quotes, a partial sale keeps it
    Given Alex holds 5 units of the Yahoo title "NWI.PA" in the first account
    And the title "NWI.PA" has a recorded quote of 21.50
    When Alex sells 2 units of the title "NWI.PA" in the first account
    Then the title "NWI.PA" is still tracked
    And the title "NWI.PA" has 1 recorded quotes
    When Alex sells 3 units of the title "NWI.PA" in the first account
    Then the title "NWI.PA" is no longer tracked
    And the title "NWI.PA" has 0 recorded quotes

  Scenario: a title bought again later starts without the old history
    Given Alex holds 5 units of the Yahoo title "NWI.PA" in the first account
    And the title "NWI.PA" has a recorded quote of 21.50
    And Alex deletes the line of the title "NWI.PA" in the first account
    When Alex holds 1 units of the Yahoo title "NWI.PA" in the first account
    Then the title "NWI.PA" is still tracked
    And the title "NWI.PA" has 0 recorded quotes

  Scenario: a title held by two accounts survives the deletion of one line
    Given Alex holds 5 units of the Yahoo title "NWI.PA" in the first account
    And Alex holds 3 units of the Yahoo title "NWI.PA" in the second account
    And the title "NWI.PA" has a recorded quote of 21.50
    When Alex deletes the line of the title "NWI.PA" in the first account
    Then the title "NWI.PA" is still tracked
    And the title "NWI.PA" has 1 recorded quotes
    When Alex sells 3 units of the title "NWI.PA" in the second account
    Then the title "NWI.PA" is no longer tracked

  Scenario: the euro cash title survives a zero balance and the deletion of its account
    Given Alex holds a cash balance of 500 in the first account
    When Alex sets the cash balance of the first account to 0
    Then the euro cash title still exists
    When Alex holds a cash balance of 500 in the first account
    And Alex deletes the first account
    Then the euro cash title still exists
