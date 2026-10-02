Feature: Cash balance

  Scenario: setting a securities account's cash balance shows the euro cash line, then removes it
    Given a securities account "Compte titres"
    When I set its cash balance to 500
    Then holdings list a cash line of 500 for "Compte titres"
    When I set its cash balance to 800
    Then holdings list a cash line of 800 for "Compte titres"
    When I set its cash balance to 0
    Then holdings list no cash line for "Compte titres"

  Scenario: a savings balance set to 0 stays at 0
    Given an account "Livret A"
    When I set its cash balance to 500
    And I set its cash balance to 0
    Then holdings list a cash line of 0 for "Livret A"
