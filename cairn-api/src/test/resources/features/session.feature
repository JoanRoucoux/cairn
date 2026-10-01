Feature: Reading the signed-in session

  Scenario: the session carries the owner and leaves the passkeys to their own call
    Given a registered passkey "iPhone de Joan" from an iCloud authenticator
    When I read the session
    Then the session carries the owner "Joan Roucoux" and no passkey list

  Scenario: a passkey is listed with the provider its authenticator discloses
    Given a registered passkey "iPhone de Joan" from an iCloud authenticator
    And a registered passkey "Cle USB" from an authenticator the API does not know
    When I read the passkeys
    Then the passkey "iPhone de Joan" is provided by "ICLOUD_KEYCHAIN"
    And the passkey "Cle USB" has no provider
    And no passkey is the current one
