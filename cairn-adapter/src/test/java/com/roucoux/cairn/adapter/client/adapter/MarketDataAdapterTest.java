package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** No Spring context: the adapter is built directly against a WireMock server. */
class MarketDataAdapterTest {

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

    private MarketDataAdapter adapter() {
        return new MarketDataAdapter(
                RestClient.builder().baseUrl(server.baseUrl()).build());
    }

    @Test
    void mapsTheQuotePayloadToAPrice() {
        server.stubFor(get(urlEqualTo("/quotes/US0378331005"))
                .willReturn(okJson("{\"isin\":\"US0378331005\",\"price\":123.45}")));

        assertThat(adapter().currentPrice("US0378331005")).contains(new BigDecimal("123.45"));
    }

    @Test
    void returnsEmptyWhenTheInstrumentIsUnknown() {
        server.stubFor(
                get(urlEqualTo("/quotes/XX0000000000")).willReturn(aResponse().withStatus(404)));

        assertThat(adapter().currentPrice("XX0000000000")).isEmpty();
    }

    @Test
    void raisesATechnicalFailureWhenTheProviderErrors() {
        server.stubFor(
                get(urlEqualTo("/quotes/US0378331005")).willReturn(aResponse().withStatus(503)));

        assertThatExceptionOfType(MarketDataUnavailableException.class)
                .isThrownBy(() -> adapter().currentPrice("US0378331005"));
    }
}
