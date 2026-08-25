Feature: Get the current price of an instrument

  Business scenarios for GET /quote/{isin}, exercised end-to-end over real HTTP.
  Reference implementation for future business-scenario features — add one .feature
  file per feature, with its own step definitions in the cucumber/ package.

  Scenario: A known instrument has a quote
    Given the market data provider knows the price of "US0378331005" as "123.45"
    When I ask for the quote of "US0378331005"
    Then the response status is 200
    And the quoted price is "123.45"

  Scenario: An unknown instrument has no quote
    Given the market data provider does not know "XX0000000000"
    When I ask for the quote of "XX0000000000"
    Then the response status is 422

  Scenario: The market data provider is unavailable
    Given the market data provider is failing for "US0378331005"
    When I ask for the quote of "US0378331005"
    Then the response status is 502
