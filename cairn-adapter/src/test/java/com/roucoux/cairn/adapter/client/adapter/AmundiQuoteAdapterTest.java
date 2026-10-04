package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

class AmundiQuoteAdapterTest {

    private static final String SHARE = "/product-services/fdr/share/v3/full/QS0000000020";

    private WireMockServer wireMock;
    private AmundiQuoteAdapter adapter;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        adapter = new AmundiQuoteAdapter(http11Client(wireMock.baseUrl()));
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private static RestClient http11Client(String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                        .build(HttpClientSettings.defaults()))
                .build();
    }

    private void stub(String field, String body) {
        wireMock.stubFor(post(urlEqualTo(SHARE))
                .withRequestBody(equalToJson("{\"fields\":[\"" + field + "\"]}"))
                .willReturn(okJson(body)));
    }

    private static String readFixture(String path) {
        try (var stream = AmundiQuoteAdapterTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Instrument fund() {
        return new Instrument(
                UUID.randomUUID(),
                "Contoso Retraite Europe (C)",
                "QS0000000020",
                "EUR",
                AssetClass.FUND,
                PriceSource.AMUNDI,
                "QS0000000020",
                null);
    }

    @Test
    void supportsOnlyAmundi() {
        assertThat(adapter.supports(PriceSource.AMUNDI)).isTrue();
        assertThat(adapter.supports(PriceSource.SG_SIRIUS)).isFalse();
    }

    @Test
    void readsTheLatestNetAssetValue() {
        stub("lastNav", readFixture("fixtures/amundi-last-nav.json"));

        Quote quote = adapter.fetch(fund());

        assertThat(quote.price()).isEqualByComparingTo("99.38");
        assertThat(quote.asOf()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(quote.currency()).isEqualTo("EUR");
        assertThat(quote.source()).isEqualTo(PriceSource.AMUNDI);
    }

    @Test
    void keepsTheCurrencyOfTheNav() {
        stub("lastNav", readFixture("fixtures/amundi-last-nav.json").replace("\"EUR\"", "\"USD\""));

        assertThat(adapter.fetch(fund()).currency()).isEqualTo("USD");
    }

    @Test
    void readsTheHistoryOldestFirst() {
        stub("navHistory", readFixture("fixtures/amundi-nav-history.json"));

        List<Quote> history = adapter.fetchHistory(fund(), LocalDate.of(2015, 1, 1));

        assertThat(history)
                .extracting(Quote::asOf)
                .containsExactly(
                        LocalDate.of(2026, 9, 8),
                        LocalDate.of(2026, 9, 21),
                        LocalDate.of(2026, 9, 29),
                        LocalDate.of(2026, 9, 30));
        assertThat(history.get(1).price()).isEqualByComparingTo("99.95");
    }

    @Test
    void filtersHistoryOnTheRequestedStartDate() {
        stub("navHistory", readFixture("fixtures/amundi-nav-history.json"));

        List<Quote> history = adapter.fetchHistory(fund(), LocalDate.of(2026, 9, 21));

        assertThat(history).extracting(Quote::asOf).first().isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(history).hasSize(3);
    }

    @Test
    void raisesWhenTheShareIsUnknown() {
        stub("lastNav", "[]");

        assertThatThrownBy(() -> adapter.fetch(fund())).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void raisesWhenTheShareHasNoNavYet() {
        stub("lastNav", "[{\"_id\":\"share-900020\"}]");

        assertThatThrownBy(() -> adapter.fetch(fund())).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void returnsNoHistoryForAShareWithoutNav() {
        stub("navHistory", "[{\"_id\":\"share-900020\"}]");

        assertThat(adapter.fetchHistory(fund(), LocalDate.of(2015, 1, 1))).isEmpty();
    }

    @Test
    void raisesWhenTheProviderFails() {
        wireMock.stubFor(post(urlEqualTo(SHARE)).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.fetch(fund())).isInstanceOf(MarketDataUnavailableException.class);
    }
}
