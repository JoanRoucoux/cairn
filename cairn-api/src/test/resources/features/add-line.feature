Feature: Adding a line with its title in one call

  Scenario: a manual line with a price is valued and never stale
    Given a brokerage account
    When I add 3 units of the manual bond "Woodgrove Notes" priced at 42.10
    Then the add answers 201
    And the line is valued at 126.30 EUR
    And the line has no source reference and is not stale

  Scenario: a manual line without a price is refused and leaves nothing behind
    Given a brokerage account
    When I add 3 units of the manual bond "Woodgrove Notes" without a price
    Then the add answers 422
    And the portfolio holds 0 titles and 0 lines

  Scenario: a Sirius line is added from its ISIN alone and waits for its first price
    Given a brokerage account
    When I add 2 units of the Sirius product QS0009876543
    Then the add answers 201
    And the line is the fund "QS0009876543" with no price yet
    And the line has the source reference "QS0009876543"

  Scenario: a second add of a tracked source and reference reuses the title
    Given a brokerage account
    And a second brokerage account
    When I add 1 unit of the Yahoo listing "NWI.PA" to the first account
    And I add 4 units of the Yahoo listing "NWI.PA" to the second account
    Then the portfolio holds 1 titles and 2 lines

  Scenario: the same ISIN at Yahoo and at Amundi gives two titles in one account
    Given a brokerage account
    When I add 1 unit of the Yahoo listing "CW8.PA" with the ISIN LU1681043599
    And I add 1 unit of the Amundi fund LU1681043599
    Then the portfolio holds 2 titles and 2 lines

  Scenario: a price is refused on a title that is not manual
    Given a brokerage account
    When I add 1 unit of the Yahoo listing "NWI.PA" priced at 10
    Then the add answers 422
