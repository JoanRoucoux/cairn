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
