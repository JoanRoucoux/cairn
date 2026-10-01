Feature: Portfolio allocation

  Scenario: the breakdown by asset class counts every line, unvalued included
    Given an account "Allocation Broker" of type PEA
    And an instrument "Allocation Tracker" quoted by YAHOO as "ALT.PA"
    And a holding of 100 units bought at 20.00
    And a quote of 22.00 EUR dated 2026-08-21
    And an instrument "Allocation Newcomer" quoted by YAHOO as "ALN.PA"
    And a holding of 10 units with no cost basis
    When I read the allocation by asset class
    Then the allocation total is 2200 EUR
    And the asset class ETF is worth 2200 EUR with a share of 1 over 2 lines

  Scenario: the breakdown by account carries the account and counts every line
    Given an account "Allocation Broker Two" of type CTO
    And an instrument "Allocation Tracker Two" quoted by YAHOO as "AL2.PA"
    And a holding of 50 units bought at 30.00
    And a quote of 40.00 EUR dated 2026-08-21
    And an instrument "Allocation Newcomer Two" quoted by YAHOO as "AN2.PA"
    And a holding of 10 units with no cost basis
    When I read the allocation by account
    Then the allocation total is 2000 EUR
    And the account "Allocation Broker Two" of type CTO is worth 2000 EUR with a share of 1 over 2 lines

  Scenario: both breakdowns agree with the portfolio
    Given an account "Allocation Broker Three" of type PEA
    And an instrument "Allocation Tracker Three" quoted by YAHOO as "AL3.PA"
    And a holding of 7 units bought at 20.00
    And a quote of 33.33 EUR dated 2026-08-21
    And an instrument "Allocation Newcomer Three" quoted by YAHOO as "AN3.PA"
    And a holding of 10 units with no cost basis
    When I read the portfolio and both allocations
    Then both allocations match the portfolio breakdowns
