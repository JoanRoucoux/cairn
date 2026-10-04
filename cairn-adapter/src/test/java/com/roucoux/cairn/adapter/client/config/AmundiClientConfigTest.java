package com.roucoux.cairn.adapter.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roucoux.cairn.adapter.client.properties.AmundiClientProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AmundiClientConfigTest {

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

    private static RestClient client() {
        return new AmundiClientConfig()
                .amundiRestClient(new AmundiClientProperties(
                        server.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(5), "Mozilla/5.0"));
    }

    @Test
    void sendsTheConfiguredUserAgent() {
        server.stubFor(post(urlEqualTo("/share"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":42}")));

        assertThat(client().post().uri("/share").retrieve().body(Value.class)).isEqualTo(new Value(42));
        server.verify(postRequestedFor(urlEqualTo("/share")).withHeader("User-Agent", equalTo("Mozilla/5.0")));
    }

    @Test
    void retriesAServerErrorOnce() {
        server.stubFor(post(urlEqualTo("/flaky"))
                .inScenario("flaky")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("up")
                .willReturn(aResponse().withStatus(503)));
        server.stubFor(post(urlEqualTo("/flaky"))
                .inScenario("flaky")
                .whenScenarioStateIs("up")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":42}")));

        assertThat(client().post().uri("/flaky").retrieve().body(Value.class)).isEqualTo(new Value(42));
    }

    private record Value(int value) {}
}
