Feature: Reading the signed-in session

  Scenario: a passkey is listed with the provider its authenticator discloses
    Given a registered passkey "iPhone de Joan" from an iCloud authenticator
    And a registered passkey "Cle USB" from an authenticator the API does not know
    When I read the session
    Then the passkey "iPhone de Joan" is provided by "iCloud"
    And the passkey "Cle USB" has no provider
    And no passkey is the current one
