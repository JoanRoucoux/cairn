package com.roucoux.cairn.adapter.client.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roucoux.cairn.adapter.client.properties.SgSiriusClientProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** No Spring context: the configuration is called directly, against a WireMock server. */
class SgSiriusClientConfigTest {

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
    void readsJsonAnnouncedAsTextHtml() {
        server.stubFor(get(urlEqualTo("/history"))
                .willReturn(aResponse().withHeader("Content-Type", "text/html").withBody("{\"value\":42}")));
        SgSiriusClientProperties properties = new SgSiriusClientProperties(
                server.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(5), "Mozilla/5.0");

        RestClient client = new SgSiriusClientConfig().sgSiriusRestClient(properties);

        assertThat(client.get().uri("/history").retrieve().body(SirusHistory.class))
                .isEqualTo(new SirusHistory(42));
        server.verify(getRequestedFor(urlEqualTo("/history")).withHeader("User-Agent", equalTo("Mozilla/5.0")));
    }

    private record SirusHistory(int value) {}
}
