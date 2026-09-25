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
                Money.eur(new BigDecimal("352889")),
                Money.eur(new BigDecimal("297")),
                Optional.of(new BigDecimal("0.0010")),
                List.of(
                        envelope(AccountType.PEA, "185430", "1204", "0.0061"),
                        envelope(AccountType.PEE, "112380", "160", "0.0014"),
                        envelope(AccountType.CRYPTO, "55079", "-1067", "-0.0192")));
        return new DailySummary(LocalDate.of(2026, 9, 23), performance);
    }

    private static EnvelopePerformance envelope(AccountType type, String value, String change, String ratio) {
        return new EnvelopePerformance(
                type,
                Money.eur(new BigDecimal(value)),
                new BigDecimal("0.1"),
                Money.eur(new BigDecimal(change)),
                Optional.ofNullable(ratio).map(BigDecimal::new));
    }

    private static final String EXPECTED_TEXT = """
            📊 <b>Cairn · Mercredi 23 septembre</b>

            💰 Patrimoine  <b>352 889 €</b>
            📈 Jour  <b>+297 €</b>  (+0,10 %)

            <pre>🟢 PEA     185 430  +1 204  +0,61%
            🟢 PEE     112 380    +160  +0,14%
            🔴 Crypto   55 079  -1 067  -1,92%</pre>""";

    @Test
    void sendsTheFormattedMessageAsHtmlToTheConfiguredChat() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        adapter(TOKEN, CHAT_ID).send(exampleSummary());

        wireMock.verify(postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage"))
                .withRequestBody(equalToJson(
                        "{\"chat_id\":\"" + CHAT_ID + "\",\"parse_mode\":\"HTML\",\"text\":"
                                + toJsonString(EXPECTED_TEXT) + "}",
                        true,
                        false)));
    }

    @Test
    void aLosingDayShowsAFallingChartAGreyDotForAFlatEnvelopeAndNoRatioWhenThereIsNone() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));
        Performance performance = new Performance(
                PerformanceRange.D1,
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("100000")),
                Money.eur(new BigDecimal("-1500")),
                Optional.of(new BigDecimal("-0.0148")),
                List.of(
                        envelope(AccountType.LIFE_INSURANCE, "60000", "0.30", "0.000005"),
                        envelope(AccountType.SAVINGS, "40000", "-1500.30", null)));

        adapter(TOKEN, CHAT_ID).send(new DailySummary(LocalDate.of(2026, 8, 14), performance));

        assertThat(sentText()).isEqualTo("""
                📊 <b>Cairn · Vendredi 14 août</b>

                💰 Patrimoine  <b>100 000 €</b>
                📉 Jour  <b>-1 500 €</b>  (-1,48 %)

                <pre>⚪ Assu. vie  60 000       0  0,00%
                🔴 Livrets    40 000  -1 500</pre>""");
    }

    private String sentText() {
        String body = wireMock.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        return new ObjectMapper().readTree(body).path("text").asString();
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
