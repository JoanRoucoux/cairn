Feature: Cash balance

  Scenario: setting an account's cash balance shows the euro cash line, then removes it
    Given an account "Livret A"
    When I set its cash balance to 500
    Then holdings list a cash line of 500 for "Livret A"
    When I set its cash balance to 800
    Then holdings list a cash line of 800 for "Livret A"
    When I set its cash balance to 0
    Then holdings list no cash line for "Livret A"
