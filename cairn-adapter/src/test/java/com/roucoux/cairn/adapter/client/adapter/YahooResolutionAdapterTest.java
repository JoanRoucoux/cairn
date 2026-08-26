package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** No Spring context: the adapter is built directly against a WireMock server. */
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
        stub("/v1/finance/search", "fixtures/yahoo-search-FR0013296084.json");

        List<InstrumentCandidate> candidates = adapter.resolve("FR0013296084");

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.sourceRef()).isEqualTo("0P0001D8GQ.F");
            assertThat(candidate.source()).isEqualTo(PriceSource.YAHOO);
            assertThat(candidate.assetClass()).isEqualTo(AssetClass.FUND);
            assertThat(candidate.name()).isEqualTo("Valmy Gestion Diversifiée");
        });
    }

    @Test
    void mapsQuoteTypesToAssetClasses() {
        assertThat(YahooResolutionAdapter.assetClassOf("EQUITY")).isEqualTo(AssetClass.EQUITY);
        assertThat(YahooResolutionAdapter.assetClassOf("ETF")).isEqualTo(AssetClass.ETF);
        assertThat(YahooResolutionAdapter.assetClassOf("MUTUALFUND")).isEqualTo(AssetClass.FUND);
    }

    @Test
    void returnsNothingForAnIsinYahooDoesNotKnow() {
        stub("/v1/finance/search", "fixtures/yahoo-search-empty.json");

        assertThat(adapter.resolve("QS0002904819")).isEmpty();
    }

    @Test
    void returnsNothingRatherThanFailingWhenYahooIsDown() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/finance/search")).willReturn(serverError()));

        assertThat(adapter.resolve("FR0013296084")).isEmpty();
    }
}
