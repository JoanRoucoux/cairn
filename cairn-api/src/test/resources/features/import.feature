Feature: Portfolio import

  Background:
    Given an account "Sample Broker" of type PEA
    And an instrument "Global Growth Tracker" quoted by YAHOO as "GGT.PA"
    And a holding of 100 units bought at 20.00
    And a quote of 22.00 EUR dated 2026-08-21

  Scenario: importing a file updates a position that already exists
    When I import:
      """
      account;accountType;institution;instrument;isinOrTicker;quantity;averageCost
      Sample Broker;PEA;Sample Broker;Global Growth Tracker;GGT.PA;120;21.00
      """
    Then the import reports 0 created and 1 updated holdings
    When I read the portfolio
    Then the total is 2640 EUR

  Scenario: a file whose rows cannot be read is refused whole
    When I import:
      """
      account;accountType;institution;instrument;isinOrTicker;quantity;averageCost
      Sample Broker;NOT_A_TYPE;Sample Broker;Global Growth Tracker;GGT.PA;120;21.00
      """
    Then the import is refused
    When I read the portfolio
    Then the total is 2200 EUR

  Scenario: a savings account refuses a line other than its balance
    When I import:
      """
      account;accountType;institution;instrument;isinOrTicker;quantity;averageCost
      Livret A;SAVINGS;Fortuneo;Global Growth Tracker;GGT.PA;10;
      """
    Then the import is refused
    When I read the portfolio
    Then the total is 2200 EUR

  Scenario: a savings balance is imported as the euro cash line
    When I import:
      """
      account;accountType;institution;instrument;isinOrTicker;quantity;averageCost
      Livret A;SAVINGS;Fortuneo;Euros;EUR;500;
      """
    Then the import reports 1 created and 0 updated holdings
    When I read the portfolio
    Then the total is 2700 EUR
