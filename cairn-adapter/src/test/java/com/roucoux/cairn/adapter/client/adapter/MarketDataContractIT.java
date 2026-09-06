package com.roucoux.cairn.adapter.client.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Hits the real providers, no WireMock. Excluded from the default Failsafe run (see
 * cairn-adapter/pom.xml), only re-enabled by the {@code external} profile: a provider outage or
 * rate limit must never fail a normal build. Asserts only the response *shape*, never a value: a
 * price changes every day, a format doesn't.
 *
 * <p>The instruments below are deliberately public and deliberately not anyone's holdings. This
 * repository is public, and a list of real positions committed here would disclose a portfolio's
 * composition as surely as its amounts would. Shape is what these tests read, so any liquid
 * instrument on each provider proves exactly as much: one venue per Yahoo listing suffix the
 * adapters have to parse, one fund per Sirius payload, one coin per CoinGecko response.
 */
@Tag("external")
class MarketDataContractIT {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    @Test
    void yahooStillQuotesEveryEuropeanVenueTheAdapterParses() {
        // Paris, Amsterdam, Xetra, Milan: the four suffixes whose payloads the adapter reads.
        List.of("MC.PA", "ASML.AS", "EUNL.DE", "SGLD.MI").forEach(symbol -> {
            Quote quote = realYahoo().fetch(etf(symbol));
            assertThat(quote.price()).isPositive();
            assertThat(quote.currency()).isEqualTo("EUR");
            assertThat(quote.asOf()).isAfter(LocalDate.now().minusDays(7));
        });
    }

    @Test
    void yahooStillResolvesAnIsinToASymbol() {
        List.of("FR0000121014", "IE00B4L5Y983")
                .forEach(isin -> assertThat(realResolver().resolve(isin)).isNotEmpty());
    }

    @Test
    void sgSiriusStillPublishesItsNetAssetValueSeries() {
        List.of("FR0010343822", "LU2010458359").forEach(isin -> {
            Quote quote = realSgSirius().fetch(fund(isin));
            assertThat(quote.price()).isPositive();
            assertThat(quote.asOf()).isAfter(LocalDate.now().minusDays(10));
        });
    }

    @Test
    void coinGeckoStillPricesACoinInEuros() {
        List.of("bitcoin", "ethereum")
                .forEach(id ->
                        assertThat(realCoinGecko().fetch(crypto(id)).price()).isPositive());
    }

    private static YahooQuoteAdapter realYahoo() {
        return new YahooQuoteAdapter(
                RestClient.builder()
                        .baseUrl("https://query1.finance.yahoo.com")
                        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                        .build(),
                Duration.ofMillis(300));
    }

    private static YahooResolutionAdapter realResolver() {
        return new YahooResolutionAdapter(RestClient.builder()
                .baseUrl("https://query1.finance.yahoo.com")
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build());
    }

    private static SgSiriusQuoteAdapter realSgSirius() {
        JacksonJsonHttpMessageConverter jsonFromHtml = new JacksonJsonHttpMessageConverter();
        jsonFromHtml.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML));
        return new SgSiriusQuoteAdapter(RestClient.builder()
                .baseUrl("https://investmentsolutions.societegenerale.fr")
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .configureMessageConverters(converters -> converters.withJsonConverter(jsonFromHtml))
                .build());
    }

    private static CoinGeckoQuoteAdapter realCoinGecko() {
        return new CoinGeckoQuoteAdapter(
                RestClient.builder().baseUrl("https://api.coingecko.com").build(),
                Clock.systemDefaultZone(),
                new SampleCoins());
    }

    private static Instrument etf(String symbol) {
        return new Instrument(UUID.randomUUID(), symbol, null, "EUR", AssetClass.ETF, PriceSource.YAHOO, symbol, null);
    }

    private static Instrument fund(String isin) {
        return new Instrument(UUID.randomUUID(), isin, isin, "EUR", AssetClass.FUND, PriceSource.SG_SIRIUS, isin, null);
    }

    private static Instrument crypto(String coinGeckoId) {
        return new Instrument(
                UUID.randomUUID(),
                coinGeckoId,
                null,
                "EUR",
                AssetClass.CRYPTO,
                PriceSource.COINGECKO,
                coinGeckoId,
                null);
    }

    private static class SampleCoins implements LoadInstrumentsPort {

        @Override
        public List<Instrument> findAll() {
            return List.of("bitcoin", "ethereum").stream()
                    .map(MarketDataContractIT::crypto)
                    .toList();
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
