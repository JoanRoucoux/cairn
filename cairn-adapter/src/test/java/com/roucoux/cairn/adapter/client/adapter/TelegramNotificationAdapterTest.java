package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.DailySummary;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class TelegramNotificationAdapterTest {

    private static final String TOKEN = "123456:super-secret-token";
    private static final String ENCODED_TOKEN = "123456%3Asuper-secret-token";
    private static final String CHAT_ID = "42";

    private WireMockServer wireMock;
    private List<Duration> sleeps;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        sleeps = new ArrayList<>();
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    /**
     * Same request factory as {@code TelegramClientConfig.telegramRestClient}, cannot reuse the
     * bean method directly (different package): the JDK HttpClient's default HTTP/2 attempts an h2c
     * upgrade on a POST with a body that WireMock's Jetty answers with a connection close, surfacing
     * as {@code ResourceAccessException: EOF reached while reading} rather than a clean HTTP/1.1
     * fallback.
     */
    private TelegramNotificationAdapter adapter(String token, String chatId) {
        TelegramClientProperties properties = new TelegramClientProperties(
                wireMock.baseUrl(), token, chatId, Duration.ofSeconds(2), Duration.ofSeconds(5));
        RestClient client = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                        .build(HttpClientSettings.defaults()))
                .build();
        return new TelegramNotificationAdapter(client, properties, new ObjectMapper(), sleeps::add);
    }

    private static DailySummary exampleSummary() {
        Performance performance = new Performance(
                PerformanceRange.D1,
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 9, 23),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("298889")),
                Money.eur(new BigDecimal("2431")),
                Optional.of(new BigDecimal("0.0082")),
                List.of(
                        envelope(AccountType.PEA, "1204", "0.0061"),
                        envelope(AccountType.PEE, "160", "0.0014"),
                        envelope(AccountType.CRYPTO, "1067", "0.0192")));
        return new DailySummary(LocalDate.of(2026, 9, 23), performance);
    }

    private static EnvelopePerformance envelope(AccountType type, String change, String ratio) {
        return new EnvelopePerformance(
                type,
                Money.eur(BigDecimal.ONE),
                new BigDecimal("0.1"),
                Money.eur(new BigDecimal(change)),
                Optional.of(new BigDecimal(ratio)));
    }

    private static final String EXPECTED_TEXT = """
            Cairn, resume du mercredi 23 septembre

            Patrimoine : 298 889 EUR
            Jour : +2 431 EUR (+0,82 %)

            PEA : +1 204 EUR (+0,61 %)
            PEE : +160 EUR (+0,14 %)
            Crypto : +1 067 EUR (+1,92 %)""";

    @Test
    void sendsTheFormattedMessageToTheConfiguredChat() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter(TOKEN, CHAT_ID).send(exampleSummary());

        wireMock.verify(postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage"))
                .withRequestBody(equalToJson(
                        "{\"chat_id\":\"" + CHAT_ID + "\",\"text\":" + toJsonString(EXPECTED_TEXT) + "}",
                        true,
                        false)));
    }

    private static String toJsonString(String text) {
        try {
            return new ObjectMapper().writeValueAsString(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void retriesOnceAfterA429ThenSucceeds() {
        wireMock.stubFor(
                post(urlPathTemplate("/bot{token}/sendMessage"))
                        .inScenario("throttled")
                        .whenScenarioStateIs("Started")
                        .willSetStateTo("retried")
                        .willReturn(
                                aResponse()
                                        .withStatus(429)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\",\"parameters\":{\"retry_after\":3}}")));
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .inScenario("throttled")
                .whenScenarioStateIs("retried")
                .willReturn(ok()));

        adapter(TOKEN, CHAT_ID).send(exampleSummary());

        wireMock.verify(2, postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage")));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void capsTheRetryDelayAtSixtySeconds() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"parameters\":{\"retry_after\":600}}")));

        assertThatThrownBy(() -> adapter(TOKEN, CHAT_ID).send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void aSecond429InARowFailsWithoutARetryLoop() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"parameters\":{\"retry_after\":1}}")));

        assertThatThrownBy(() -> adapter(TOKEN, CHAT_ID).send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class);

        wireMock.verify(2, postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage")));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void aBadRequestFailsWithoutAnyRetry() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> adapter(TOKEN, CHAT_ID).send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage")));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void anUnauthorizedTokenFailsWithoutAnyRetryAndNeverLeaksTheToken() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> adapter(TOKEN, CHAT_ID).send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure -> {
                    assertThat(failure.getMessage()).doesNotContain(TOKEN);
                    assertThat(failure.getMessage()).doesNotContain(wireMock.baseUrl());
                });
    }

    @Test
    void aConnectionResetNeverLeaksTheTokenInTheMessageOrTheCauseChain() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter(TOKEN, CHAT_ID).send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure -> {
                    Throwable current = failure;
                    while (current != null) {
                        if (current.getMessage() != null) {
                            assertThat(current.getMessage()).doesNotContain(TOKEN);
                        }
                        current = current.getCause();
                    }
                });
    }

    @Test
    void noAttemptIsMadeAndNoTokenLeaksWhenTheTokenIsMissing() {
        assertThatThrownBy(() -> adapter("", CHAT_ID).send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(TOKEN));

        wireMock.verify(0, postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage")));
    }

    @Test
    void noAttemptIsMadeWhenTheChatIdIsMissing() {
        assertThatThrownBy(() -> adapter(TOKEN, "").send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class);

        wireMock.verify(0, postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage")));
    }

    @Test
    void theTokenNeverAppearsInAnyExceptionMessageOnAServerError() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter(TOKEN, CHAT_ID).send(exampleSummary()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(TOKEN));
    }
}
