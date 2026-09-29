Feature: Managing accounts

  Scenario: an account can be renamed
    Given an account named "Saxo" of type CTO
    When I update it to "Saxo Investor" of type PEA at "Saxo Bank"
    Then the accounts list "Saxo Investor" of type PEA at "Saxo Bank"

  Scenario: renaming an account to another account's name is refused
    Given an account named "Saxo" of type CTO
    And an account named "Binance" of type CRYPTO
    When I update "Binance" to "Saxo" of type CRYPTO at "Binance"
    Then the update answers 409

  Scenario: an account holding only its cash balance is deleted with it
    Given an account named "Fortuneo" of type SAVINGS
    And its cash balance is 1500
    When I delete it
    Then the deletion answers 204
    And no holding is left for that account

  Scenario: an account that still holds a line cannot be deleted
    Given an account named "Fortuneo" of type SAVINGS
    And it holds 20000 units of a manual cash instrument "Livret A"
    When I delete it
    Then the deletion answers 422
    And the accounts still list "Fortuneo"
