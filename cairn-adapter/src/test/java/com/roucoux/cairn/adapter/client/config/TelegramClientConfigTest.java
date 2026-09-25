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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
    void buildsAClientOnTheConfiguredBaseUrlWithNoTokenAndNoChatIdBound() {
        server.stubFor(get(urlEqualTo("/health")).willReturn(ok("reached")));
        TelegramClientProperties properties =
                new TelegramClientProperties(server.baseUrl(), "", "", Duration.ofSeconds(2), Duration.ofSeconds(5));

        RestClient client = new TelegramClientConfig().telegramRestClient(properties);

        assertThat(client.get().uri("/health").retrieve().body(String.class)).isEqualTo("reached");
    }

    @Test
    void postsARecordBodyOverHttp11WithoutAnH2cUpgradeFailure() {
        server.stubFor(post(urlEqualTo("/postBody")).willReturn(ok()));
        TelegramClientProperties properties =
                new TelegramClientProperties(server.baseUrl(), "", "", Duration.ofSeconds(2), Duration.ofSeconds(5));

        RestClient client = new TelegramClientConfig().telegramRestClient(properties);

        assertThat(client.post()
                        .uri("/postBody")
                        .body(new Body("42", "hello"))
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode()
                        .is2xxSuccessful())
                .isTrue();
    }

    private record Body(String chatId, String text) {}
}
