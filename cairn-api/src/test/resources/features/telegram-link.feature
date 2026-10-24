Feature: Telegram link

  Scenario: linking and unlinking the Telegram chat
    Given the Telegram link is reset
    And the Telegram bot is configured as "cairn_test_bot"
    When I start a Telegram link
    Then the invitation URL starts with "https://t.me/cairn_test_bot?start="
    When the bot receives the start command of that token from chat 42
    And I check the Telegram link
    Then the Telegram status is "linked"
    When I unlink Telegram
    Then the Telegram status is "unlinked"
