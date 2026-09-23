package com.roucoux.cairn.adapter.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** No Spring context: the interceptor sits on a plain RestClient, against a WireMock server. */
class TransientFailureRetryInterceptorTest {

    private static final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private final List<Duration> waits = new ArrayList<>();

    @BeforeAll
    static void startServer() {
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void reset() {
        server.resetAll();
        waits.clear();
    }

    @Test
    void retriesAfterAConnectionFailure() {
        stubSequence(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER), ok("price"));

        assertThat(call()).isEqualTo("price");
        assertThat(waits).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void retriesAServerErrorWithAGrowingWait() {
        stubSequence(aResponse().withStatus(503), aResponse().withStatus(502), ok("price"));

        assertThat(call()).isEqualTo("price");
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(3));
    }

    @Test
    void honoursAShortRetryAfterOnTooManyRequests() {
        stubSequence(aResponse().withStatus(429).withHeader("Retry-After", "2"), ok("price"));

        assertThat(call()).isEqualTo("price");
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void givesUpAtOnceWhenRetryAfterIsTooLong() {
        stubSequence(aResponse().withStatus(429).withHeader("Retry-After", "60"), ok("price"));

        assertThatThrownBy(this::call).isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        assertThat(waits).isEmpty();
    }

    @Test
    void neverRetriesAClientError() {
        stubSequence(aResponse().withStatus(404), ok("price"));

        assertThatThrownBy(this::call).isInstanceOf(HttpClientErrorException.NotFound.class);
        server.verify(1, getRequestedFor(urlEqualTo("/quote")));
    }

    @Test
    void stopsAfterTwoRetries() {
        stubSequence(
                aResponse().withStatus(503),
                aResponse().withStatus(503),
                aResponse().withStatus(503),
                ok("price"));

        assertThatThrownBy(this::call).isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        server.verify(3, getRequestedFor(urlEqualTo("/quote")));
    }

    @Test
    void rethrowsTheLastConnectionFailure() {
        stubSequence(
                aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER),
                aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER),
                aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        assertThatThrownBy(this::call).isInstanceOf(ResourceAccessException.class);
    }

    private String call() {
        RestClient client = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestInterceptor(new TransientFailureRetryInterceptor(
                        List.of(Duration.ofSeconds(1), Duration.ofSeconds(3)), waits::add))
                .build();
        return client.get().uri("/quote").retrieve().body(String.class);
    }

    private static void stubSequence(com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder... responses) {
        for (int i = 0; i < responses.length; i++) {
            server.stubFor(get(urlEqualTo("/quote"))
                    .inScenario("sequence")
                    .whenScenarioStateIs(i == 0 ? STARTED : "step" + i)
                    .willSetStateTo("step" + (i + 1))
                    .willReturn(responses[i]));
        }
    }
}
