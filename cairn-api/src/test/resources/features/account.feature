Feature: Managing accounts

  Scenario: an account can be renamed
    Given an account named "Contoso Trading" of type CTO
    When I update it to "Northwind PEA" of type PEA at "Northwind Bank"
    Then the accounts list "Northwind PEA" of type PEA at "Northwind Bank"

  Scenario: renaming an account to another account's name is refused
    Given an account named "Contoso Trading" of type CTO
    And an account named "Tailspin Wallet" of type CRYPTO
    When I update "Tailspin Wallet" to "Contoso Trading" of type CRYPTO at "Tailspin Exchange"
    Then the update answers 409

  Scenario: an account holding only its cash balance is deleted with it
    Given an account named "Livret A" of type SAVINGS
    And its cash balance is 1500
    When I delete it
    Then the deletion answers 204
    And no holding is left for that account

  Scenario: an account that still holds a line cannot be deleted
    Given an account named "Contoso Trading" of type CTO
    And it holds 20000 units of a manual cash instrument "Livret A"
    When I delete it
    Then the deletion answers 422
    And the accounts still list "Contoso Trading"

  Scenario: an account holding a line cannot become a savings account
    Given an account named "Contoso Trading" of type CTO
    And it holds 20000 units of a manual cash instrument "Livret A"
    When I update it to "Contoso Trading" of type SAVINGS at "Contoso Securities"
    Then the update answers 422
