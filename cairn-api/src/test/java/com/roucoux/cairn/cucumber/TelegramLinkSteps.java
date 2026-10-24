package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.domain.model.StartCommand;
import com.roucoux.cairn.domain.port.out.TelegramBotPort;
import com.roucoux.cairn.generated.model.TelegramInvitationResponse;
import com.roucoux.cairn.generated.model.TelegramLinkResponse;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.util.UriComponentsBuilder;

public class TelegramLinkSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    @Autowired
    private TelegramBotPort telegramBot;

    private TelegramInvitationResponse invitation;
    private TelegramLinkResponse link;

    @Before
    public void resetTelegramState() {
        invitation = null;
        link = null;
    }

    @Given("the Telegram link is reset")
    public void theTelegramLinkIsReset() {
        jdbc.update("update telegram_link set chat_id = null, chat_label = null, linked_at = null,"
                + " pending_token_hash = null, pending_expires_at = null");
    }

    @Given("the Telegram bot is configured as {string}")
    public void theTelegramBotIsConfiguredAs(String username) {
        when(telegramBot.configured()).thenReturn(true);
        when(telegramBot.botUsername()).thenReturn(username);
    }

    @When("I start a Telegram link")
    public void iStartATelegramLink() {
        invitation = restTemplate.postForObject("/session/telegram/link", null, TelegramInvitationResponse.class);
    }

    @Then("the invitation URL starts with {string}")
    public void theInvitationUrlStartsWith(String prefix) {
        assertThat(invitation.getUrl()).startsWith(prefix);
    }

    @When("the bot receives the start command of that token from chat {long}")
    public void theBotReceivesTheStartCommandOfThatToken(long chatId) {
        String token = UriComponentsBuilder.fromUri(URI.create(invitation.getUrl()))
                .build()
                .getQueryParams()
                .getFirst("start");
        when(telegramBot.drainStartCommands()).thenReturn(List.of(new StartCommand(chatId, "Alex", token)));
    }

    @When("I check the Telegram link")
    public void iCheckTheTelegramLink() {
        link = restTemplate.postForObject("/session/telegram/link/check", null, TelegramLinkResponse.class);
    }

    @When("I unlink Telegram")
    public void iUnlinkTelegram() {
        restTemplate.delete("/session/telegram/link");
        link = restTemplate.getForObject("/session/telegram", TelegramLinkResponse.class);
    }

    @Then("the Telegram status is {string}")
    public void theTelegramStatusIs(String status) {
        assertThat(link.getStatus().getValue()).isEqualTo(status);
    }
}
