package com.roucoux.cairn.adapter.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.web.client.RestClient;

class TelegramClientConfigTest {

    private static final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void startServer() {
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void buildsAClientOnTheConfiguredBaseUrlWithNoTokenBound() {
        server.stubFor(get(urlEqualTo("/health")).willReturn(ok("reached")));
        TelegramClientProperties properties =
                new TelegramClientProperties(server.baseUrl(), "", Duration.ofSeconds(2), Duration.ofSeconds(5));

        RestClient client = new TelegramClientConfig().telegramRestClient(properties);

        assertThat(client.get().uri("/health").retrieve().body(String.class)).isEqualTo("reached");
    }

    @Test
    void postsARecordBodyOverHttp11WithoutAnH2cUpgradeFailure() {
        server.stubFor(post(urlEqualTo("/postBody")).willReturn(ok()));
        TelegramClientProperties properties =
                new TelegramClientProperties(server.baseUrl(), "", Duration.ofSeconds(2), Duration.ofSeconds(5));

        RestClient client = new TelegramClientConfig().telegramRestClient(properties);

        assertThat(client.post()
                        .uri("/postBody")
                        .body(new Body(42L, "hello"))
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode()
                        .is2xxSuccessful())
                .isTrue();
    }

    @Test
    void telegramMessagesResolvesBothLanguagesFromUtf8Bundles() {
        MessageSource messages = new TelegramClientConfig().telegramMessages();

        assertThat(messages.getMessage("netWorth", null, Locale.FRANCE)).isEqualTo("Patrimoine");
        assertThat(messages.getMessage("netWorth", null, Locale.UK)).isEqualTo("Net worth");
        assertThat(messages.getMessage("change.year", null, Locale.FRANCE)).isEqualTo("Année");
        assertThat(messages.getMessage("linked", null, Locale.FRANCE))
                .isEqualTo("✅ Cairn est connecté. Les récaps arriveront ici.");
        assertThat(messages.getMessage("linked", null, Locale.UK))
                .isEqualTo("✅ Cairn is connected. Your recaps will arrive here.");
    }

    private record Body(long chatId, String text) {}
}
