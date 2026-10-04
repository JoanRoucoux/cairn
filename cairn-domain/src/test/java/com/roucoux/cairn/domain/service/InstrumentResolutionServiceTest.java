package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class InstrumentResolutionServiceTest {

    private static final InstrumentCandidate ETF_CANDIDATE = new InstrumentCandidate(
            "Amundi MSCI World",
            PriceSource.YAHOO,
            "ETF.PA",
            AssetClass.ETF,
            "Paris",
            null,
            "ETF",
            BigDecimal.TEN,
            "EUR");
    private static final InstrumentCandidate SG_CANDIDATE = new InstrumentCandidate(
            "Societe Generale",
            PriceSource.SG_SIRIUS,
            "QS0000000010",
            AssetClass.EQUITY,
            "Paris",
            null,
            "GLE",
            BigDecimal.ONE,
            "EUR");

    @Test
    void returnsEveryCandidateFoundAcrossSources() {
        InstrumentResolutionService service = service(List.of(resolver(ETF_CANDIDATE), resolver()), List.of());

        assertThat(service.resolve("LU0000000010")).containsExactly(ETF_CANDIDATE);
    }

    @Test
    void keepsCandidatesFromEverySourceThatAnswers() {
        InstrumentResolutionService service =
                service(List.of(resolver(ETF_CANDIDATE), resolver(SG_CANDIDATE)), List.of());

        assertThat(service.resolve("QS0000000010")).containsExactly(ETF_CANDIDATE, SG_CANDIDATE);
    }

    @Test
    void ignoresASourceThatFailsRatherThanLosingTheOthers() {
        InstrumentResolutionService service = service(List.of(failingResolver(), resolver(SG_CANDIDATE)), List.of());

        assertThat(service.resolve("QS0000000010")).containsExactly(SG_CANDIDATE);
    }

    @Test
    void raisesWhenNoSourceKnowsTheInstrument() {
        InstrumentResolutionService service = service(List.of(resolver()), List.of());

        assertThatThrownBy(() -> service.resolve("XX0000000000")).isInstanceOf(UnknownInstrumentException.class);
    }

    @Test
    void probesALivePriceForEachCandidate() {
        ResolveInstrumentPort yahoo = resolver(new InstrumentCandidate(
                "Amundi MSCI World",
                PriceSource.YAHOO,
                "CW8.PA",
                AssetClass.ETF,
                "Paris",
                "LU1681043599",
                "CW8.PA",
                null,
                null));
        FetchQuotePort quotes = fetcher(PriceSource.YAHOO, instrument -> new BigDecimal("559.30"));

        List<InstrumentCandidate> candidates =
                service(List.of(yahoo), List.of(quotes)).resolve("amundi world");

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.exchange()).isEqualTo("Paris");
            assertThat(candidate.isin()).isEqualTo("LU1681043599");
            assertThat(candidate.symbol()).isEqualTo("CW8.PA");
            assertThat(candidate.probePrice()).isEqualByComparingTo("559.30");
        });
    }

    @Test
    void aFailedProbeKeepsTheCandidateWithoutAPrice() {
        ResolveInstrumentPort yahoo = resolver(new InstrumentCandidate(
                "Accor", PriceSource.YAHOO, "AC.PA", AssetClass.EQUITY, "Paris", null, "AC.PA", null, null));
        FetchQuotePort failing = fetcher(PriceSource.YAHOO, instrument -> {
            throw new MarketDataUnavailableException("yahoo down");
        });

        List<InstrumentCandidate> candidates =
                service(List.of(yahoo), List.of(failing)).resolve("accor");

        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.probePrice()).isNull());
    }

    @Test
    void aProbeThatTakesTooLongKeepsTheCandidateWithoutAPrice() {
        ResolveInstrumentPort yahoo = resolver(new InstrumentCandidate(
                "Accor", PriceSource.YAHOO, "AC.PA", AssetClass.EQUITY, "Paris", null, "AC.PA", null, null));
        FetchQuotePort slow = fetcher(PriceSource.YAHOO, instrument -> {
            try {
                Thread.sleep(Duration.ofSeconds(2).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new BigDecimal("30.00");
        });

        List<InstrumentCandidate> candidates =
                service(List.of(yahoo), List.of(slow), Duration.ofMillis(50)).resolve("accor");

        assertThat(candidates)
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.probePrice()).isNull());
    }

    @Test
    void theProbeQuoteGivesTheCandidateItsCurrency() {
        ResolveInstrumentPort yahoo = resolver(candidate("AAPL", null));
        FetchQuotePort quotes = fetcher(PriceSource.YAHOO, instrument -> BigDecimal.TEN, "USD");

        assertThat(service(List.of(yahoo), List.of(quotes)).resolve("aapl"))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.currency()).isEqualTo("USD"));
    }

    @Test
    void theProbeInstrumentDoesNotClaimToBeInEuro() {
        List<String> seen = new CopyOnWriteArrayList<>();
        ResolveInstrumentPort yahoo = resolver(candidate("AAPL", null));
        FetchQuotePort quotes = fetcher(
                PriceSource.YAHOO,
                instrument -> {
                    seen.add(instrument.currency());
                    return BigDecimal.TEN;
                },
                "USD");

        service(List.of(yahoo), List.of(quotes)).resolve("aapl");

        assertThat(seen).isNotEmpty().doesNotContain("EUR");
    }

    @Test
    void aFailedProbeLeavesTheCurrencyUnknown() {
        ResolveInstrumentPort yahoo = resolver(candidate("AC.PA", null));
        FetchQuotePort failing = fetcher(PriceSource.YAHOO, instrument -> {
            throw new MarketDataUnavailableException("yahoo down");
        });

        assertThat(service(List.of(yahoo), List.of(failing)).resolve("accor"))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.currency()).isNull());
    }

    @Test
    void sortsEuroListingsFirstThenUnknownThenOtherCurrenciesKeepingTheSourceOrder() {
        InstrumentCandidate xetra = candidate("XETRA", "EUR");
        InstrumentCandidate lse = candidate("LSE", "USD");
        InstrumentCandidate amsterdam = candidate("AMS", "EUR");
        InstrumentCandidate unknown = candidate("UNK", null);
        InstrumentCandidate swiss = candidate("SWX", "CHF");

        List<InstrumentCandidate> sorted = service(List.of(resolver(lse, xetra, swiss, unknown, amsterdam)), List.of())
                .resolve("world");

        assertThat(sorted).containsExactly(xetra, amsterdam, unknown, lse, swiss);
    }

    private static InstrumentResolutionService service(
            List<ResolveInstrumentPort> resolvers, List<FetchQuotePort> fetchers) {
        return service(resolvers, fetchers, List.of());
    }

    private static InstrumentResolutionService service(
            List<ResolveInstrumentPort> resolvers, List<FetchQuotePort> fetchers, List<Instrument> tracked) {
        return new InstrumentResolutionService(resolvers, fetchers, new StubLoadInstruments(tracked));
    }

    private static InstrumentResolutionService service(
            List<ResolveInstrumentPort> resolvers, List<FetchQuotePort> fetchers, Duration probeTimeout) {
        return new InstrumentResolutionService(resolvers, fetchers, new StubLoadInstruments(List.of()), probeTimeout);
    }

    private static InstrumentCandidate candidate(String ref, String currency) {
        return new InstrumentCandidate(
                ref, PriceSource.YAHOO, ref, AssetClass.ETF, "Exchange", null, ref, null, currency);
    }

    private static FetchQuotePort fetcher(PriceSource source, Function<Instrument, BigDecimal> priceFor) {
        return fetcher(source, priceFor, "EUR");
    }

    private static FetchQuotePort fetcher(
            PriceSource source, Function<Instrument, BigDecimal> priceFor, String currency) {
        return new FetchQuotePort() {
            @Override
            public boolean supports(PriceSource candidate) {
                return candidate == source;
            }

            @Override
            public Quote fetch(Instrument instrument) {
                return new Quote(
                        instrument.id(), LocalDate.now(), priceFor.apply(instrument), currency, source, Instant.now());
            }

            @Override
            public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
                return List.of();
            }
        };
    }

    private static ResolveInstrumentPort resolver(InstrumentCandidate... candidates) {
        return new StubResolveInstrumentPort(List.of(candidates));
    }

    private static ResolveInstrumentPort resolverFor(PriceSource source, InstrumentCandidate... candidates) {
        return new SourceResolveInstrumentPort(source, List.of(candidates));
    }

    private static ResolveInstrumentPort failingResolverFor(PriceSource source) {
        return new SourceResolveInstrumentPort(source, null);
    }

    private static final class SourceResolveInstrumentPort implements ResolveInstrumentPort {
        private final PriceSource source;
        private final List<InstrumentCandidate> candidates;

        private SourceResolveInstrumentPort(PriceSource source, List<InstrumentCandidate> candidates) {
            this.source = source;
            this.candidates = candidates;
        }

        @Override
        public boolean supports(PriceSource candidate) {
            return candidate == source;
        }

        @Override
        public List<InstrumentCandidate> resolve(String query) {
            if (candidates == null) {
                throw new MarketDataUnavailableException("simulated failure for " + query);
            }
            return candidates;
        }
    }

    private record StubLoadInstruments(List<Instrument> instruments) implements LoadInstrumentsPort {
        @Override
        public List<Instrument> findAll() {
            return instruments;
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

    private static ResolveInstrumentPort failingResolver() {
        return new FailingResolveInstrumentPort();
    }

    private static final class StubResolveInstrumentPort implements ResolveInstrumentPort {
        private final List<InstrumentCandidate> candidates;

        private StubResolveInstrumentPort(List<InstrumentCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public boolean supports(PriceSource source) {
            return true;
        }

        @Override
        public List<InstrumentCandidate> resolve(String query) {
            return candidates;
        }
    }

    private static final class FailingResolveInstrumentPort implements ResolveInstrumentPort {
        @Override
        public boolean supports(PriceSource source) {
            return true;
        }

        @Override
        public List<InstrumentCandidate> resolve(String query) {
            throw new MarketDataUnavailableException("simulated failure for " + query);
        }
    }

    @Test
    void importResolutionKeepsYahooAndAmundiAheadOfCoinGecko() {
        InstrumentCandidate coin = new InstrumentCandidate(
                "Bitcoin", PriceSource.COINGECKO, "bitcoin", AssetClass.CRYPTO, null, null, "BTC", null, null);
        InstrumentResolutionService service = service(
                List.of(resolverFor(PriceSource.COINGECKO, coin), resolverFor(PriceSource.YAHOO, ETF_CANDIDATE)),
                List.of());

        assertThat(service.resolve("bitcoin")).containsExactly(ETF_CANDIDATE);
    }

    @Test
    void importResolutionFallsBackToACoinGeckoIdMatchedExactly() {
        InstrumentCandidate bitcoin = new InstrumentCandidate(
                "Bitcoin", PriceSource.COINGECKO, "bitcoin", AssetClass.CRYPTO, null, null, "BTC", null, "EUR");
        InstrumentCandidate cash = new InstrumentCandidate(
                "Bitcoin Cash",
                PriceSource.COINGECKO,
                "bitcoin-cash",
                AssetClass.CRYPTO,
                null,
                null,
                "BCH",
                null,
                "EUR");
        InstrumentResolutionService service = service(
                List.of(resolverFor(PriceSource.COINGECKO, cash, bitcoin), resolverFor(PriceSource.YAHOO)), List.of());

        assertThat(service.resolve("Bitcoin")).containsExactly(bitcoin);
    }

    @Test
    void importResolutionNeverAsksCoinGeckoForAnIsin() {
        InstrumentCandidate coin = new InstrumentCandidate(
                "Odd", PriceSource.COINGECKO, "LU0000000010", AssetClass.CRYPTO, null, null, null, null, "EUR");
        InstrumentResolutionService service =
                service(List.of(resolverFor(PriceSource.COINGECKO, coin), resolverFor(PriceSource.YAHOO)), List.of());

        assertThatThrownBy(() -> service.resolve("LU0000000010")).isInstanceOf(UnknownInstrumentException.class);
    }

    @Test
    void importResolutionStaysUnknownWhenCoinGeckoHasNoExactIdOrIsDown() {
        InstrumentCandidate cash = new InstrumentCandidate(
                "Bitcoin Cash",
                PriceSource.COINGECKO,
                "bitcoin-cash",
                AssetClass.CRYPTO,
                null,
                null,
                "BCH",
                null,
                "EUR");

        assertThatThrownBy(() -> service(List.of(resolverFor(PriceSource.COINGECKO, cash)), List.of())
                        .resolve("bitcoin"))
                .isInstanceOf(UnknownInstrumentException.class);
        assertThatThrownBy(() -> service(List.of(failingResolverFor(PriceSource.COINGECKO)), List.of())
                        .resolve("bitcoin"))
                .isInstanceOf(UnknownInstrumentException.class);
    }

    @Test
    void importResolutionAsksYahooThenAmundiInThatOrder() {
        InstrumentCandidate fund = amundiFund(null);
        InstrumentResolutionService service = service(
                List.of(
                        resolverFor(PriceSource.COINGECKO),
                        resolverFor(PriceSource.YAHOO, ETF_CANDIDATE),
                        resolverFor(PriceSource.AMUNDI, fund)),
                List.of());

        assertThat(service.resolve("LU0000000010")).containsExactly(ETF_CANDIDATE, fund);
    }

    @Test
    void searchAsksOnlyTheResolverOfTheRequestedSource() {
        InstrumentCandidate fund = amundiFund(null);
        InstrumentResolutionService service = service(
                List.of(failingResolverFor(PriceSource.YAHOO), resolverFor(PriceSource.AMUNDI, fund)), List.of());

        assertThat(service.search(PriceSource.AMUNDI, "QS0000000020")).containsExactly(fund);
    }

    @Test
    void searchAnswersAnEmptyListRatherThanRaisingWhenTheSourceKnowsNothing() {
        InstrumentResolutionService service = service(List.of(resolverFor(PriceSource.YAHOO)), List.of());

        assertThat(service.search(PriceSource.YAHOO, "zzzz")).isEmpty();
    }

    @Test
    void searchAnswersAnEmptyListForASourceWithoutResolver() {
        InstrumentResolutionService service =
                service(List.of(resolverFor(PriceSource.YAHOO, ETF_CANDIDATE)), List.of());

        assertThat(service.search(PriceSource.COINGECKO, "bitcoin")).isEmpty();
    }

    @Test
    void searchPropagatesASourceThatIsDownInsteadOfLookingEmpty() {
        InstrumentResolutionService service = service(List.of(failingResolverFor(PriceSource.YAHOO)), List.of());

        assertThatThrownBy(() -> service.search(PriceSource.YAHOO, "accor"))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void searchProbesAndSortsEuroFirst() {
        LocalDate quoteDate = LocalDate.of(2026, 10, 2);
        FetchQuotePort quotes = new FetchQuotePort() {
            @Override
            public boolean supports(PriceSource candidate) {
                return candidate == PriceSource.YAHOO;
            }

            @Override
            public Quote fetch(Instrument instrument) {
                return new Quote(instrument.id(), quoteDate, BigDecimal.TEN, "EUR", PriceSource.YAHOO, Instant.now());
            }

            @Override
            public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
                return List.of();
            }
        };
        InstrumentCandidate usd = new InstrumentCandidate(
                "Apple", PriceSource.YAHOO, "AAPL", AssetClass.EQUITY, "NASDAQ", null, "AAPL", BigDecimal.ONE, "USD");
        InstrumentResolutionService service =
                service(List.of(resolverFor(PriceSource.YAHOO, usd, candidate("AC.PA", null))), List.of(quotes));

        List<InstrumentCandidate> found = service.search(PriceSource.YAHOO, "a");

        assertThat(found).extracting(InstrumentCandidate::sourceRef).containsExactly("AC.PA", "AAPL");
        assertThat(found.getFirst().probePrice()).isEqualByComparingTo("10");
        assertThat(found.getFirst().probeAsOf()).isEqualTo(quoteDate);
    }

    @Test
    void searchKeepsTheProbeDateTheSourceAlreadyGave() {
        LocalDate navDate = LocalDate.of(2026, 9, 24);
        InstrumentResolutionService service =
                service(List.of(resolverFor(PriceSource.AMUNDI, amundiFund(navDate))), List.of());

        assertThat(service.search(PriceSource.AMUNDI, "QS0000000020"))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.probeAsOf()).isEqualTo(navDate));
    }

    @Test
    void searchMarksACandidateWhoseSourceAndReferenceAreAlreadyTracked() {
        UUID trackedId = UUID.randomUUID();
        Instrument tracked = new Instrument(
                trackedId, "Amundi MSCI World", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "ETF.PA", null);
        Instrument otherSource = new Instrument(
                UUID.randomUUID(), "Other", null, "EUR", AssetClass.ETF, PriceSource.AMUNDI, "AC.PA", null);
        InstrumentResolutionService service = service(
                List.of(resolverFor(PriceSource.YAHOO, ETF_CANDIDATE, candidate("AC.PA", "EUR"))),
                List.of(),
                List.of(tracked, otherSource));

        assertThat(service.search(PriceSource.YAHOO, "etf"))
                .extracting(InstrumentCandidate::trackedInstrumentId)
                .containsExactly(trackedId, null);
    }

    private static InstrumentCandidate amundiFund(LocalDate navDate) {
        return new InstrumentCandidate(
                "Contoso Retraite Europe",
                PriceSource.AMUNDI,
                "QS0000000020",
                AssetClass.FUND,
                null,
                "QS0000000020",
                null,
                new BigDecimal("99.38"),
                "EUR",
                navDate,
                null);
    }

    @Test
    void logsASourceThatIsUnavailable() {
        InstrumentResolutionService service = service(List.of(failingResolver(), resolver(SG_CANDIDATE)), List.of());

        try (CapturedLog log = CapturedLog.of(InstrumentResolutionService.class)) {
            service.resolve("QS0000000010");

            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getMessage()).contains("QS0000000010").contains("simulated failure");
            });
        }
    }

    @Test
    void logsAProbeThatTimesOut() {
        ResolveInstrumentPort yahoo = resolver(new InstrumentCandidate(
                "Woodgrove", PriceSource.YAHOO, "WGV.PA", AssetClass.EQUITY, "Paris", null, "WGV.PA", null, null));
        FetchQuotePort slow = fetcher(PriceSource.YAHOO, instrument -> {
            try {
                Thread.sleep(Duration.ofSeconds(2).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new BigDecimal("30.00");
        });

        try (CapturedLog log = CapturedLog.of(InstrumentResolutionService.class)) {
            service(List.of(yahoo), List.of(slow), Duration.ofMillis(50)).resolve("woodgrove");

            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getMessage()).contains("WGV.PA").contains("YAHOO");
            });
        }
    }
}
