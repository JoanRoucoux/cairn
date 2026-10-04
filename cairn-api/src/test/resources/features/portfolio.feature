Feature: Portfolio valuation

  Scenario: the portfolio totals every holding at its latest known price
    Given an account "Sample Broker" of type PEA
    And an instrument "Global Growth Tracker" quoted by YAHOO as "GGT.PA"
    And a holding of 100 units bought at 20.00
    And a quote of 22.00 EUR dated 2026-08-21
    When I read the portfolio
    Then the total is 2200 EUR
    And the unrealized gain is 200 EUR

  Scenario: a holding without a cost basis reports no unrealized gain
    Given an account "Sample Broker Two" of type CTO
    And an instrument "Acme Corp" quoted by YAHOO as "ACM.PA"
    And a holding of 50 units with no cost basis
    And a quote of 40.00 EUR dated 2026-08-21
    When I read the portfolio
    Then the total is 2000 EUR
    And no unrealized gain is reported

  Scenario: a holding whose instrument has no quote yet still appears in the portfolio
    Given an account "Sample Broker Three" of type CTO
    And an instrument "Brand New Fund" quoted by YAHOO as "BNF.F"
    And a holding of 10 units with no cost basis
    When I read the portfolio
    Then the portfolio lists 1 holding with no price
    And the unvalued count is 1

  Scenario: a holding quoted in another currency is excluded from the total and counted
    Given an account "Sample Broker Four" of type CTO
    And an instrument "Euro Tracker" quoted by YAHOO as "EUT.PA"
    And a holding of 10 units with no cost basis
    And a quote of 50.00 EUR dated 2026-08-21
    And a USD instrument "Wall Street Tracker" quoted by YAHOO as "WST"
    And a holding of 4 units with no cost basis
    And a quote of 80.00 USD dated 2026-08-21
    When I read the portfolio
    Then the total is 500 EUR
    And the non-EUR count is 1
    And the unvalued count is 0
    And the portfolio lists the USD holding priced in USD without a value in EUR
    And the instrument "WST" is stored in USD
    And the one day performance totals 500 EUR
