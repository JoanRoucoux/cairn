package com.roucoux.cairn.adapter.client.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

/** No Spring context: the adapter is built directly against a WireMock server. */
class SgSiriusQuoteAdapterTest {

    private WireMockServer wireMock;
    private SgSiriusQuoteAdapter adapter;

    @BeforeEach
    void startStub() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        JacksonJsonHttpMessageConverter jsonFromHtml = new JacksonJsonHttpMessageConverter();
        jsonFromHtml.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML));
        adapter = new SgSiriusQuoteAdapter(RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .configureMessageConverters(converters -> converters.withJsonConverter(jsonFromHtml))
                .build());
    }

    @AfterEach
    void stopStub() {
        wireMock.stop();
    }

    private void stubTextHtml(String fixture) {
        wireMock.stubFor(get(urlPathMatching(".*/liquidative/"))
                .willReturn(aResponse().withHeader("Content-Type", "text/html").withBody(readFixture(fixture))));
    }

    private static String readFixture(String path) {
        try (var stream = SgSiriusQuoteAdapterTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Instrument fcpe(String isin) {
        return new Instrument(UUID.randomUUID(), isin, isin, "EUR", AssetClass.FUND, PriceSource.SG_SIRIUS, isin, null);
    }

    @Test
    void supportsOnlySgSirius() {
        assertThat(adapter.supports(PriceSource.SG_SIRIUS)).isTrue();
        assertThat(adapter.supports(PriceSource.YAHOO)).isFalse();
    }

    @Test
    void readsTheLatestNetAssetValue() {
        stubTextHtml("fixtures/sg-sirius-QS0002904819.json");

        Quote quote = adapter.fetch(fcpe("QS0002904819"));

        assertThat(quote.price()).isEqualByComparingTo("63.33");
        assertThat(quote.asOf()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(quote.source()).isEqualTo(PriceSource.SG_SIRIUS);
    }

    @Test
    void parsesJsonServedAsTextHtml() {
        stubTextHtml("fixtures/sg-sirius-QS0002904819.json");

        assertThatNoException().isThrownBy(() -> adapter.fetch(fcpe("QS0002904819")));
    }

    @Test
    void readsTheWholeHistoryFromTheSameSingleCall() {
        stubTextHtml("fixtures/sg-sirius-QS0002904819.json");

        List<Quote> history = adapter.fetchHistory(fcpe("QS0002904819"), LocalDate.of(2001, 1, 1));

        assertThat(history).hasSizeGreaterThan(5000);
        assertThat(history.getFirst().asOf()).isEqualTo(LocalDate.of(2001, 1, 2));
    }

    @Test
    void filtersHistoryOnTheRequestedStartDate() {
        stubTextHtml("fixtures/sg-sirius-QS0002904819.json");

        List<Quote> history = adapter.fetchHistory(fcpe("QS0002904819"), LocalDate.of(2026, 1, 1));

        assertThat(history).allSatisfy(quote -> assertThat(quote.asOf()).isAfterOrEqualTo(LocalDate.of(2026, 1, 1)));
    }

    @Test
    void raisesOnAnEmptyBody() {
        wireMock.stubFor(get(urlPathMatching(".*/liquidative/")).willReturn(ok("[]")));

        assertThatThrownBy(() -> adapter.fetch(fcpe("QS0002904819")))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void raisesWhenTheProviderFails() {
        wireMock.stubFor(get(urlPathMatching(".*/liquidative/")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.fetch(fcpe("QS0002904819")))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
