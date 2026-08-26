package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** No Spring context: the adapter is built directly against a WireMock server. */
class YahooQuoteAdapterTest {

    private WireMockServer wireMock;
    private YahooQuoteAdapter adapter;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        adapter = new YahooQuoteAdapter(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(), Duration.ZERO);
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private void stub(String path, String fixture) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(okJson(readFixture(fixture))));
    }

    private static String readFixture(String path) {
        try (var stream = YahooQuoteAdapterTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Instrument etf(String sourceRef) {
        return new Instrument(
                UUID.randomUUID(),
                "Amundi MSCI World",
                null,
                "EUR",
                AssetClass.ETF,
                PriceSource.YAHOO,
                sourceRef,
                null);
    }

    private static Instrument fund(String sourceRef) {
        return new Instrument(
                UUID.randomUUID(),
                "Amundi Opportunites",
                null,
                "EUR",
                AssetClass.FUND,
                PriceSource.YAHOO,
                sourceRef,
                null);
    }

    @Test
    void supportsOnlyYahoo() {
        assertThat(adapter.supports(PriceSource.YAHOO)).isTrue();
        assertThat(adapter.supports(PriceSource.COINGECKO)).isFalse();
    }

    @Test
    void readsTheCurrentPriceAndItsDate() {
        stub("/v8/finance/chart/CW8.PA", "fixtures/yahoo-chart-CW8-1d.json");

        Quote quote = adapter.fetch(etf("CW8.PA"));

        assertThat(quote.price()).isEqualByComparingTo("688.75");
        assertThat(quote.currency()).isEqualTo("EUR");
        assertThat(quote.source()).isEqualTo(PriceSource.YAHOO);
    }

    @Test
    void datesAQuoteOnTheSessionItBelongsToNotOnTheCallDate() {
        stub("/v8/finance/chart/0P0000YPZB.F", "fixtures/yahoo-chart-0P0000YPZB-1mo.json");

        Quote quote = adapter.fetch(fund("0P0000YPZB.F"));

        assertThat(quote.asOf()).isEqualTo(LocalDate.of(2026, 8, 24));
    }

    @Test
    void skipsNullClosesWhenReadingHistory() {
        stub("/v8/finance/chart/0P0000YPZB.F", "fixtures/yahoo-chart-0P0000YPZB-1mo.json");

        List<Quote> history = adapter.fetchHistory(fund("0P0000YPZB.F"), LocalDate.of(2026, 1, 1));

        assertThat(history)
                .isNotEmpty()
                .allSatisfy(quote -> assertThat(quote.price()).isNotNull());
        assertThat(history).extracting(Quote::asOf).doesNotHaveDuplicates();
    }

    @Test
    void raisesWhenTheProviderFails() {
        wireMock.stubFor(get(urlPathMatching("/v8/finance/chart/.*")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.fetch(etf("CW8.PA"))).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void raisesWhenTheSymbolIsUnknown() {
        wireMock.stubFor(get(urlPathMatching("/v8/finance/chart/.*"))
                .willReturn(okJson("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\"}}}")));

        assertThatThrownBy(() -> adapter.fetch(etf("NOPE.PA"))).isInstanceOf(MarketDataUnavailableException.class);
    }
}
