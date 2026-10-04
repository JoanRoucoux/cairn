package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CoinGeckoResolutionAdapterTest {

    private WireMockServer wireMock;
    private CoinGeckoResolutionAdapter adapter;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        adapter = new CoinGeckoResolutionAdapter(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build());
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private void stubSearch(String fixture) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/search")).willReturn(okJson(readFixture(fixture))));
    }

    private void stubPrices(String fixture) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/simple/price")).willReturn(okJson(readFixture(fixture))));
    }

    private static String readFixture(String path) {
        try (var stream = CoinGeckoResolutionAdapterTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void supportsOnlyCoinGecko() {
        assertThat(adapter.supports(PriceSource.COINGECKO)).isTrue();
        assertThat(adapter.supports(PriceSource.YAHOO)).isFalse();
    }

    @Test
    void turnsTheTopEightCoinsIntoCryptoCandidatesPricedInOneCall() {
        stubSearch("fixtures/coingecko-search.json");
        stubPrices("fixtures/coingecko-search-prices.json");

        List<InstrumentCandidate> candidates = adapter.resolve("sol");

        assertThat(candidates).hasSize(8);
        assertThat(candidates.getFirst()).satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("Solana");
            assertThat(candidate.source()).isEqualTo(PriceSource.COINGECKO);
            assertThat(candidate.sourceRef()).isEqualTo("solana");
            assertThat(candidate.symbol()).isEqualTo("SOL");
            assertThat(candidate.assetClass()).isEqualTo(AssetClass.CRYPTO);
            assertThat(candidate.isin()).isNull();
            assertThat(candidate.exchange()).isNull();
            assertThat(candidate.probePrice()).isEqualByComparingTo("108.33");
            assertThat(candidate.currency()).isEqualTo("EUR");
            assertThat(candidate.probeAsOf()).isEqualTo(LocalDate.of(2026, 10, 4));
        });
        wireMock.verify(
                1,
                getRequestedFor(urlPathEqualTo("/api/v3/simple/price"))
                        .withQueryParam(
                                "ids", equalTo("solana,nowhere-staked-sol,coin-3,coin-4,coin-5,coin-6,coin-7,coin-8"))
                        .withQueryParam("vs_currencies", equalTo("eur"))
                        .withQueryParam("include_last_updated_at", equalTo("true")));
    }

    @Test
    void leavesACoinWithoutAPriceUnpriced() {
        stubSearch("fixtures/coingecko-search.json");
        stubPrices("fixtures/coingecko-search-prices.json");

        assertThat(adapter.resolve("sol").get(1)).satisfies(candidate -> {
            assertThat(candidate.sourceRef()).isEqualTo("nowhere-staked-sol");
            assertThat(candidate.symbol()).isEqualTo("NSSOL");
            assertThat(candidate.probePrice()).isNull();
            assertThat(candidate.currency()).isNull();
            assertThat(candidate.probeAsOf()).isNull();
        });
    }

    @Test
    void doesNotPriceWhenNoCoinMatches() {
        stubSearch("fixtures/coingecko-search-empty.json");

        assertThat(adapter.resolve("zzzz")).isEmpty();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v3/simple/price")));
    }

    @Test
    void raisesWhenTheSearchFails() {
        wireMock.stubFor(get(anyUrl()).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.resolve("sol")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void raisesWhenThePriceCallFails() {
        stubSearch("fixtures/coingecko-search.json");
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/simple/price")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.resolve("sol")).isInstanceOf(MarketDataUnavailableException.class);
    }
}
