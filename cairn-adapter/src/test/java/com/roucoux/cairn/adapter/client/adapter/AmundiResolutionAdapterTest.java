package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

class AmundiResolutionAdapterTest {

    private static final String SHARE = "/product-services/fdr/share/v3/full/QS0000000020";

    private WireMockServer wireMock;
    private AmundiResolutionAdapter adapter;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        adapter = new AmundiResolutionAdapter(http11Client(wireMock.baseUrl()));
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

    private void stub(String body) {
        wireMock.stubFor(post(urlEqualTo(SHARE))
                .withRequestBody(equalToJson("{\"fields\":[\"label\",\"lastNav\"]}"))
                .willReturn(okJson(body)));
    }

    private static String readFixture(String path) {
        try (var stream = AmundiResolutionAdapterTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void supportsOnlyAmundi() {
        assertThat(adapter.supports(PriceSource.AMUNDI)).isTrue();
        assertThat(adapter.supports(PriceSource.YAHOO)).isFalse();
    }

    @Test
    void resolvesAnIsinToAFundWithItsLatestNav() {
        stub(readFixture("fixtures/amundi-label-and-last-nav.json"));

        List<InstrumentCandidate> candidates = adapter.resolve(" qs0000000020 ");

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("Contoso Retraite Europe (C)");
            assertThat(candidate.source()).isEqualTo(PriceSource.AMUNDI);
            assertThat(candidate.sourceRef()).isEqualTo("QS0000000020");
            assertThat(candidate.isin()).isEqualTo("QS0000000020");
            assertThat(candidate.assetClass()).isEqualTo(AssetClass.FUND);
            assertThat(candidate.exchange()).isNull();
            assertThat(candidate.symbol()).isNull();
            assertThat(candidate.probePrice()).isEqualByComparingTo("99.38");
            assertThat(candidate.currency()).isEqualTo("EUR");
            assertThat(candidate.probeAsOf()).isEqualTo(java.time.LocalDate.of(2026, 9, 30));
        });
    }

    @Test
    void offersAShareWithoutNavYet() {
        stub(readFixture("fixtures/amundi-label-only.json"));

        assertThat(adapter.resolve("QS0000000020")).singleElement().satisfies(candidate -> {
            assertThat(candidate.probePrice()).isNull();
            assertThat(candidate.currency()).isNull();
            assertThat(candidate.probeAsOf()).isNull();
        });
    }

    @Test
    void returnsNothingForAnUnknownIsin() {
        stub("[]");

        assertThat(adapter.resolve("QS0000000020")).isEmpty();
    }

    @Test
    void returnsNothingForAShareWithoutLabel() {
        stub("[{\"_id\":\"share-900020\"}]");

        assertThat(adapter.resolve("QS0000000020")).isEmpty();
    }

    @Test
    void neverCallsAmundiForATicker() {
        assertThat(adapter.resolve("CW8.PA")).isEmpty();
        assertThat(adapter.resolve("amundi")).isEmpty();
        wireMock.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void raisesWhenTheProviderFailsDuringLookup() {
        wireMock.stubFor(post(urlEqualTo(SHARE)).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.resolve("QS0000000020")).isInstanceOf(MarketDataUnavailableException.class);
    }
}
