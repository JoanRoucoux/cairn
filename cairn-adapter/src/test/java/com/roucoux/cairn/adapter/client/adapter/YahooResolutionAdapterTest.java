package com.roucoux.cairn.adapter.client.adapter;

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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class YahooResolutionAdapterTest {

    private WireMockServer wireMock;
    private YahooResolutionAdapter adapter;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        adapter = new YahooResolutionAdapter(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build());
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private void stub(String path, String fixture) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(okJson(readFixture(fixture))));
    }

    private static String readFixture(String path) {
        try (var stream = YahooResolutionAdapterTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void supportsOnlyYahoo() {
        assertThat(adapter.supports(PriceSource.YAHOO)).isTrue();
        assertThat(adapter.supports(PriceSource.COINGECKO)).isFalse();
    }

    @Test
    void mapsAFundIsinToItsMorningstarSymbol() {
        stub("/v1/finance/search", "fixtures/yahoo-search-fund-isin.json");

        List<InstrumentCandidate> candidates = adapter.resolve("FR0000000010");

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.sourceRef()).isEqualTo("0P0000000A.F");
            assertThat(candidate.source()).isEqualTo(PriceSource.YAHOO);
            assertThat(candidate.assetClass()).isEqualTo(AssetClass.FUND);
            assertThat(candidate.name()).isEqualTo("Fonds Exemple Diversifié");
            assertThat(candidate.exchange()).isEqualTo("Frankfurt");
            assertThat(candidate.symbol()).isEqualTo("0P0000000A.F");
            assertThat(candidate.isin()).isEqualTo("FR0000000010");
        });
    }

    @Test
    void leavesTheIsinOutWhenTheQueryIsAName() {
        stub("/v1/finance/search", "fixtures/yahoo-search-fund-isin.json");

        List<InstrumentCandidate> candidates = adapter.resolve("fonds exemple");

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.isin()).isNull();
            assertThat(candidate.symbol()).isEqualTo("0P0000000A.F");
        });
    }

    @Test
    void readsTheIsinFromALowerCaseQuery() {
        stub("/v1/finance/search", "fixtures/yahoo-search-fund-isin.json");

        assertThat(adapter.resolve(" fr0000000010 "))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.isin()).isEqualTo("FR0000000010"));
    }

    @Test
    void mapsQuoteTypesToAssetClasses() {
        assertThat(YahooResolutionAdapter.assetClassOf("EQUITY")).contains(AssetClass.EQUITY);
        assertThat(YahooResolutionAdapter.assetClassOf("ETF")).contains(AssetClass.ETF);
        assertThat(YahooResolutionAdapter.assetClassOf("MUTUALFUND")).contains(AssetClass.FUND);
        assertThat(YahooResolutionAdapter.assetClassOf("CRYPTOCURRENCY")).contains(AssetClass.CRYPTO);
        assertThat(YahooResolutionAdapter.assetClassOf("INDEX")).isEmpty();
        assertThat(YahooResolutionAdapter.assetClassOf(null)).isEmpty();
    }

    @Test
    void offersOnlyListingsCairnCanHold() {
        stub("/v1/finance/search", "fixtures/yahoo-search-mixed-types.json");

        assertThat(adapter.resolve("CAC 40"))
                .extracting(InstrumentCandidate::sourceRef)
                .containsExactly("AI.PA", "CW8.PA", "0P0000FUND.F", "BTC-EUR");
    }

    @Test
    void keepsTheFirstFiveHoldableListingsWhenOthersCrowdTheTop() {
        stub("/v1/finance/search", "fixtures/yahoo-search-crowded.json");

        assertThat(adapter.resolve("air"))
                .extracting(InstrumentCandidate::sourceRef)
                .containsExactly("AI.PA", "F1.PA", "F2.PA", "F3.PA", "F4.PA");
        wireMock.verify(
                getRequestedFor(urlPathEqualTo("/v1/finance/search")).withQueryParam("quotesCount", equalTo("10")));
    }

    @Test
    void offersNothingForAQuoteWithoutAType() {
        stub("/v1/finance/search", "fixtures/yahoo-search-no-quote-type.json");

        assertThat(adapter.resolve("Mystery")).isEmpty();
    }

    @Test
    void returnsNothingForAnIsinYahooDoesNotKnow() {
        stub("/v1/finance/search", "fixtures/yahoo-search-empty.json");

        assertThat(adapter.resolve("QS0000000010")).isEmpty();
    }

    @Test
    void fallsBackToTheShortNameWhenThereIsNoLongName() {
        stub("/v1/finance/search", "fixtures/yahoo-search-shortname-crypto.json");

        List<InstrumentCandidate> candidates = adapter.resolve("sol");

        assertThat(candidates).hasSize(2);
        assertThat(candidates.getFirst()).satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("Fidelity Solana Fund");
            assertThat(candidate.assetClass()).isEqualTo(AssetClass.ETF);
        });
        assertThat(candidates.get(1)).satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("Solana USD");
            assertThat(candidate.assetClass()).isEqualTo(AssetClass.CRYPTO);
        });
    }

    @Test
    void raisesRatherThanLookingEmptyWhenYahooIsDown() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/finance/search")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.resolve("FR0000000010")).isInstanceOf(MarketDataUnavailableException.class);
    }
}
