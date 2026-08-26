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
 */
@Tag("external")
class MarketDataContractIT {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    @Test
    void yahooStillQuotesEveryListedInstrumentOfThePortfolio() {
        List.of("AC.PA", "FDJU.PA", "RACE.MI", "MMS.PA", "CW8.PA", "ESE.PA", "EUEA.AS", "WPEA.PA", "UST.PA", "GLE.PA")
                .forEach(symbol -> {
                    Quote quote = realYahoo().fetch(etf(symbol));
                    assertThat(quote.price()).isPositive();
                    assertThat(quote.currency()).isEqualTo("EUR");
                    assertThat(quote.asOf()).isAfter(LocalDate.now().minusDays(7));
                });
    }

    @Test
    void yahooStillResolvesEveryFundIsinOfThePortfolio() {
        List.of("FR0013296084", "FR0013296076", "FR0013296316", "FR0013451226", "FR0013296092", "QS0009108166")
                .forEach(isin -> assertThat(realResolver().resolve(isin)).isNotEmpty());
    }

    @Test
    void sgSiriusStillPublishesBothEmployeeShareFunds() {
        List.of("QS0002904819", "QS0002965737").forEach(isin -> {
            Quote quote = realSgSirius().fetch(fcpe(isin));
            assertThat(quote.price()).isPositive();
            assertThat(quote.asOf()).isAfter(LocalDate.now().minusDays(10));
        });
    }

    @Test
    void coinGeckoStillPricesEveryCoinOfThePortfolio() {
        List.of("ethereum", "binancecoin", "binance-staked-sol", "ripple", "bitcoin")
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
                new PortfolioCoins());
    }

    private static Instrument etf(String symbol) {
        return new Instrument(UUID.randomUUID(), symbol, null, "EUR", AssetClass.ETF, PriceSource.YAHOO, symbol, null);
    }

    private static Instrument fcpe(String isin) {
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

    private static class PortfolioCoins implements LoadInstrumentsPort {

        @Override
        public List<Instrument> findAll() {
            return List.of("ethereum", "binancecoin", "binance-staked-sol", "ripple", "bitcoin").stream()
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
