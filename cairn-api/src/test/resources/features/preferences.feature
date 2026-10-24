Feature: Preferences

  Scenario: the language can be changed without touching the theme
    Given the preferences are reset
    When I set the language to en
    Then the preferences are language en and theme system
