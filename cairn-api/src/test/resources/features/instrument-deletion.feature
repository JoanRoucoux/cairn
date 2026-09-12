Feature: Instrument deletion

  Scenario: deleting an instrument removes its holdings and quotes
    Given an instrument to delete "Global Growth Tracker" priced by YAHOO as "GGT2.PA"
    And a holding to delete of 10 units bought at 20.00
    And a quote to delete of 22.00 EUR dated 2026-08-21
    When I delete the instrument
    Then the instrument is gone
    And the holding is gone
    And the quote is gone

  Scenario: deleting an instrument twice answers not found the second time
    Given an instrument to delete "Global Growth Tracker" priced by YAHOO as "GGT3.PA"
    And the instrument is already deleted
    When I delete the instrument
    Then the deletion answers not found
