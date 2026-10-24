package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.roucoux.cairn.adapter.client.properties.TelegramClientProperties;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class TelegramBotApiTest {

    private static final String TOKEN = "123456:super-secret-token";
    private static final String ENCODED_TOKEN = "123456%3Asuper-secret-token";
    private static final long CHAT_ID = 42L;
    private static final String TEXT = "hello";

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

    private TelegramBotApi api(String token) {
        TelegramClientProperties properties =
                new TelegramClientProperties(wireMock.baseUrl(), token, Duration.ofSeconds(2), Duration.ofSeconds(5));
        RestClient client = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                        .build(HttpClientSettings.defaults()))
                .build();
        return new TelegramBotApi(client, properties, new ObjectMapper(), sleeps::add);
    }

    @Test
    void sendsTheTextAsHtmlToTheGivenChat() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage")).willReturn(ok()));

        api(TOKEN).sendMessage(CHAT_ID, TEXT);

        wireMock.verify(postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage"))
                .withRequestBody(
                        equalToJson("{\"chat_id\":42,\"parse_mode\":\"HTML\",\"text\":\"hello\"}", true, false)));
    }

    @Test
    void isConfiguredOnlyWithAToken() {
        assertThat(api(TOKEN).configured()).isTrue();
        assertThat(api("").configured()).isFalse();
        assertThat(api(null).configured()).isFalse();
    }

    @Test
    void getMeReturnsTheResult() {
        wireMock.stubFor(get(urlPathTemplate("/bot{token}/getMe"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":{\"username\":\"cairn_bot\"}}")));

        assertThat(api(TOKEN).getMe().path("username").asString()).isEqualTo("cairn_bot");
    }

    @Test
    void getUpdatesPostsTheOffsetTheLimitAndNoLongPolling() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/getUpdates"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":[]}")));

        assertThat(api(TOKEN).getUpdates(7L, 1).isArray()).isTrue();

        wireMock.verify(postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/getUpdates"))
                .withRequestBody(equalToJson(
                        "{\"offset\":7,\"limit\":1,\"timeout\":0,\"allowed_updates\":[\"message\"]}", true, false)));
    }

    @Test
    void getUpdatesOmitsTheOffsetWhenThereIsNone() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/getUpdates"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":[]}")));

        api(TOKEN).getUpdates(null, 100);

        wireMock.verify(postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/getUpdates"))
                .withRequestBody(equalToJson("{\"limit\":100,\"timeout\":0}", true, true))
                .withRequestBody(notMatching(".*offset.*")));
    }

    @Test
    void anUnparsableAnswerIsADeliveryFailure() {
        wireMock.stubFor(get(urlPathTemplate("/bot{token}/getMe"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("not json")));

        assertThatThrownBy(() -> api(TOKEN).getMe()).isInstanceOf(NotificationDeliveryException.class);
    }

    @Test
    void aConflictFromAWebhookIsADeliveryFailure() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/getUpdates"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":false,\"error_code\":409,\"description\":\"webhook is active\"}")));

        assertThatThrownBy(() -> api(TOKEN).getUpdates(null, 100))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure ->
                        assertThat(failure.getMessage()).doesNotContain(TOKEN).doesNotContain("webhook"));
    }

    @Test
    void aServerErrorOnGetUpdatesIsADeliveryFailureWithoutTheToken() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/getUpdates"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> api(TOKEN).getUpdates(null, 100))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure -> {
                    assertThat(failure.getMessage()).doesNotContain(TOKEN);
                    assertThat(failure.getMessage()).doesNotContain(wireMock.baseUrl());
                });
    }

    @Test
    void aConnectionResetOnGetUpdatesNeverLeaksTheToken() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/getUpdates"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> api(TOKEN).getUpdates(null, 100))
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

        api(TOKEN).sendMessage(CHAT_ID, TEXT);

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

        assertThatThrownBy(() -> api(TOKEN).sendMessage(CHAT_ID, TEXT))
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

        assertThatThrownBy(() -> api(TOKEN).sendMessage(CHAT_ID, TEXT))
                .isInstanceOf(NotificationDeliveryException.class);

        wireMock.verify(2, postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage")));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void aBadRequestFailsWithoutAnyRetry() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> api(TOKEN).sendMessage(CHAT_ID, TEXT))
                .isInstanceOf(NotificationDeliveryException.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/bot" + ENCODED_TOKEN + "/sendMessage")));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void anUnauthorizedTokenFailsWithoutAnyRetryAndNeverLeaksTheToken() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> api(TOKEN).sendMessage(CHAT_ID, TEXT))
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

        assertThatThrownBy(() -> api(TOKEN).sendMessage(CHAT_ID, TEXT))
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
        assertThatThrownBy(() -> api("").sendMessage(CHAT_ID, TEXT))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(TOKEN));

        wireMock.verify(0, postRequestedFor(urlMatching(".*/sendMessage")));
    }

    @Test
    void theTokenNeverAppearsInAnyExceptionMessageOnAServerError() {
        wireMock.stubFor(post(urlPathTemplate("/bot{token}/sendMessage"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> api(TOKEN).sendMessage(CHAT_ID, TEXT))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(TOKEN));
    }
}
