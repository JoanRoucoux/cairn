package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** No Spring context: the adapter is built directly against a WireMock server. */
class CoinGeckoQuoteAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneId.of("Europe/Paris"));

    private static final List<Instrument> TRACKED_COINS = List.of(
            crypto("ethereum"),
            crypto("binancecoin"),
            crypto("binance-staked-sol"),
            crypto("ripple"),
            crypto("bitcoin"));

    private WireMockServer wireMock;
    private CoinGeckoQuoteAdapter adapter;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        adapter = new CoinGeckoQuoteAdapter(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(), CLOCK, new FakeLoadInstrumentsPort());
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private void stub(String path, String fixture) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(okJson(readFixture(fixture))));
    }

    private static String readFixture(String path) {
        try (var stream = CoinGeckoQuoteAdapterTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Instrument crypto(String sourceRef) {
        return new Instrument(
                UUID.randomUUID(), sourceRef, null, "EUR", AssetClass.CRYPTO, PriceSource.COINGECKO, sourceRef, null);
    }

    @Test
    void supportsOnlyCoinGecko() {
        assertThat(adapter.supports(PriceSource.COINGECKO)).isTrue();
        assertThat(adapter.supports(PriceSource.YAHOO)).isFalse();
    }

    @Test
    void readsThePriceInEuros() {
        stub("/api/v3/simple/price", "fixtures/coingecko-simple-price.json");

        Quote quote = adapter.fetch(crypto("ethereum"));

        assertThat(quote.price()).isEqualByComparingTo("2121.4");
        assertThat(quote.currency()).isEqualTo("EUR");
    }

    @Test
    void groupsEveryCoinIntoASingleCallWithinOneRefresh() {
        stub("/api/v3/simple/price", "fixtures/coingecko-simple-price.json");

        adapter.fetch(crypto("ethereum"));
        adapter.fetch(crypto("bitcoin"));
        adapter.fetch(crypto("ripple"));

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v3/simple/price")));
    }

    @Test
    void datesTheQuoteOnTheCallDayBecauseCryptoTradesContinuously() {
        stub("/api/v3/simple/price", "fixtures/coingecko-simple-price.json");

        assertThat(adapter.fetch(crypto("ethereum")).asOf()).isEqualTo(LocalDate.now(CLOCK));
    }

    @Test
    void raisesWhenTheCoinIsAbsentFromTheResponse() {
        stub("/api/v3/simple/price", "fixtures/coingecko-simple-price.json");

        assertThatThrownBy(() -> adapter.fetch(crypto("dogecoin"))).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void readsDailyHistory() {
        stub("/api/v3/coins/ethereum/market_chart", "fixtures/coingecko-market-chart.json");

        List<Quote> history = adapter.fetchHistory(crypto("ethereum"), LocalDate.of(2026, 6, 1));

        assertThat(history).isNotEmpty();
        assertThat(history).extracting(Quote::asOf).doesNotHaveDuplicates();
    }

    @Test
    void raisesWhenThePriceProviderFails() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/simple/price")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.fetch(crypto("ethereum"))).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void raisesWhenTheHistoryProviderFails() {
        wireMock.stubFor(
                get(urlPathEqualTo("/api/v3/coins/ethereum/market_chart")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.fetchHistory(crypto("ethereum"), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    private static class FakeLoadInstrumentsPort implements LoadInstrumentsPort {

        @Override
        public List<Instrument> findAll() {
            return TRACKED_COINS;
        }

        @Override
        public Optional<Instrument> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
            throw new UnsupportedOperationException();
        }
    }
}
