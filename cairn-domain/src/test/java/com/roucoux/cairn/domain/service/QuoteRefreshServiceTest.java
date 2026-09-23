package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteRefreshServiceTest {

    private static final Instrument ETHEREUM = new Instrument(
            UUID.randomUUID(), "Ethereum", null, "EUR", AssetClass.CRYPTO, PriceSource.COINGECKO, "ethereum", null);
    private static final Instrument LIVRET_A = new Instrument(
            UUID.randomUUID(),
            "Livret A",
            null,
            "EUR",
            AssetClass.CASH,
            PriceSource.MANUAL,
            null,
            "Livret d'epargne reglementee");
    private static final Instrument ETF2 = new Instrument(
            UUID.randomUUID(), "Amundi PEA S&P 500", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "ETF2.PA", null);
    private static final Instrument ETF = new Instrument(
            UUID.randomUUID(), "Amundi MSCI World", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "ETF.PA", null);

    @Test
    void routesAnInstrumentToTheAdapterThatSupportsItsSource() {
        RecordingPort yahoo = new RecordingPort(PriceSource.YAHOO);
        RecordingPort coinGecko = new RecordingPort(PriceSource.COINGECKO);
        QuoteRefreshService service = service(List.of(yahoo, coinGecko), List.of(ETHEREUM));

        service.refreshAll(Set.of(AssetClass.CRYPTO), RefreshTrigger.MANUAL);

        assertThat(coinGecko.calls()).containsExactly(ETHEREUM.id());
        assertThat(yahoo.calls()).isEmpty();
    }

    @Test
    void neverRefreshesAManuallyPricedInstrument() {
        RecordingPort yahoo = new RecordingPort(PriceSource.YAHOO);
        QuoteRefreshService service = service(List.of(yahoo), List.of(LIVRET_A));

        RefreshReport report = service.refreshAll(Set.of(AssetClass.CASH), RefreshTrigger.MANUAL);

        assertThat(yahoo.calls()).isEmpty();
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.refreshed()).isZero();
    }

    @Test
    void oneFailingInstrumentDoesNotStopTheOthers() {
        FetchQuotePort failing = new FailingPort(PriceSource.YAHOO, ETF2.id());
        QuoteRefreshService service = service(List.of(failing), List.of(ETF2, ETF));

        RefreshReport report = service.refreshAll(Set.of(AssetClass.ETF), RefreshTrigger.MANUAL);

        assertThat(report.refreshed()).isEqualTo(1);
        assertThat(report.failures())
                .singleElement()
                .satisfies(failure -> assertThat(failure.instrumentId()).isEqualTo(ETF2.id()));
    }

    @Test
    void anUnexpectedFailureDoesNotStopTheOthersEither() {
        FetchQuotePort crashing = new CrashingPort(PriceSource.YAHOO, ETF2.id());
        QuoteRefreshService service = service(List.of(crashing), List.of(ETF2, ETF));

        RefreshReport report = service.refreshAll(Set.of(AssetClass.ETF), RefreshTrigger.MANUAL);

        assertThat(report.refreshed()).isEqualTo(1);
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.instrumentId()).isEqualTo(ETF2.id());
            assertThat(failure.message()).isEqualTo("NullPointerException");
        });
    }

    @Test
    void recordsEveryFailureThroughThePort() {
        RecordingFailurePort failures = new RecordingFailurePort();
        QuoteRefreshService service =
                service(List.of(new FailingPort(PriceSource.YAHOO, ETF2.id())), List.of(ETF2), failures);

        service.refreshAll(Set.of(AssetClass.ETF), RefreshTrigger.MANUAL);

        assertThat(failures.recorded()).hasSize(1);
    }

    @Test
    void anInstrumentDeletedDuringTheRunIsNotRecordedAsAFailure() {
        RecordingFailurePort failures = new RecordingFailurePort();
        QuoteRefreshService service = new QuoteRefreshService(
                List.of(new CrashingPort(PriceSource.YAHOO, ETF2.id())),
                new StubLoadInstrumentsPort(List.of(ETF2, ETF), Set.of(ETF2.id())),
                new NoOpSaveQuotePort(),
                failures,
                new NoOpAnnounceQuotesUseCase());

        RefreshReport report = service.refreshAll(Set.of(AssetClass.ETF), RefreshTrigger.MANUAL);

        assertThat(report.refreshed()).isEqualTo(1);
        assertThat(failures.recorded()).isEmpty();
    }

    @Test
    void failsLoudlyWhenNoAdapterSupportsTheSource() {
        QuoteRefreshService service = service(List.of(), List.of(ETF));

        assertThatThrownBy(() -> service.refresh(ETF))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("YAHOO");
    }

    @Test
    void announcesEachSavedQuoteThenTheEndOfTheRefresh() {
        Instrument aaa = new Instrument(
                UUID.randomUUID(), "AAA", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "AAA.PA", null);
        Instrument bbb = new Instrument(
                UUID.randomUUID(), "BBB", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "BBB.PA", null);
        Instrument ccc = new Instrument(
                UUID.randomUUID(), "CCC", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "CCC.PA", null);
        RecordingAnnounceUseCase announced = new RecordingAnnounceUseCase(Map.of(
                aaa.id(), "AAA",
                bbb.id(), "BBB",
                ccc.id(), "CCC"));
        FetchQuotePort fetcher = new FailingPort(PriceSource.YAHOO, ccc.id());
        QuoteRefreshService service = new QuoteRefreshService(
                List.of(fetcher),
                new StubLoadInstrumentsPort(List.of(aaa, bbb, ccc)),
                new NoOpSaveQuotePort(),
                new RecordingFailurePort(),
                announced);

        service.refreshAll(Set.of(AssetClass.ETF), RefreshTrigger.MANUAL);

        assertThat(announced.events()).containsExactly("saved:AAA", "saved:BBB", "completed:2:1:MANUAL");
    }

    @Test
    void announcesAQuoteOnlyAfterItWasSaved() {
        Instrument aaa = new Instrument(
                UUID.randomUUID(), "AAA", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "AAA.PA", null);
        Instrument bbb = new Instrument(
                UUID.randomUUID(), "BBB", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "BBB.PA", null);
        RecordingAnnounceUseCase announced = new RecordingAnnounceUseCase(Map.of(aaa.id(), "AAA", bbb.id(), "BBB"));
        QuoteRefreshService service = new QuoteRefreshService(
                List.of(new RecordingPort(PriceSource.YAHOO)),
                new StubLoadInstrumentsPort(List.of(aaa, bbb)),
                new FailingSaveQuotePort(bbb.id()),
                new RecordingFailurePort(),
                announced);

        service.refreshAll(Set.of(AssetClass.ETF), RefreshTrigger.MANUAL);

        assertThat(announced.events()).containsExactly("saved:AAA", "completed:1:1:MANUAL");
    }

    private static QuoteRefreshService service(List<FetchQuotePort> fetchers, List<Instrument> instruments) {
        return service(fetchers, instruments, new RecordingFailurePort());
    }

    private static QuoteRefreshService service(
            List<FetchQuotePort> fetchers, List<Instrument> instruments, RecordQuoteFailurePort failurePort) {
        return new QuoteRefreshService(
                fetchers,
                new StubLoadInstrumentsPort(instruments),
                new NoOpSaveQuotePort(),
                failurePort,
                new NoOpAnnounceQuotesUseCase());
    }

    private static final class RecordingPort implements FetchQuotePort {
        private final PriceSource source;
        private final List<UUID> calls = new ArrayList<>();

        private RecordingPort(PriceSource source) {
            this.source = source;
        }

        @Override
        public boolean supports(PriceSource candidate) {
            return candidate == source;
        }

        @Override
        public Quote fetch(Instrument instrument) {
            calls.add(instrument.id());
            return new Quote(
                    instrument.id(), LocalDate.now(), BigDecimal.TEN, instrument.currency(), source, Instant.now());
        }

        @Override
        public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
            return List.of();
        }

        List<UUID> calls() {
            return calls;
        }
    }

    private static final class FailingPort implements FetchQuotePort {
        private final PriceSource source;
        private final UUID failingInstrumentId;

        private FailingPort(PriceSource source, UUID failingInstrumentId) {
            this.source = source;
            this.failingInstrumentId = failingInstrumentId;
        }

        @Override
        public boolean supports(PriceSource candidate) {
            return candidate == source;
        }

        @Override
        public Quote fetch(Instrument instrument) {
            if (instrument.id().equals(failingInstrumentId)) {
                throw new MarketDataUnavailableException("simulated failure for " + instrument.name());
            }
            return new Quote(
                    instrument.id(), LocalDate.now(), BigDecimal.TEN, instrument.currency(), source, Instant.now());
        }

        @Override
        public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
            return List.of();
        }
    }

    private static final class CrashingPort implements FetchQuotePort {
        private final PriceSource source;
        private final UUID crashingInstrumentId;

        private CrashingPort(PriceSource source, UUID crashingInstrumentId) {
            this.source = source;
            this.crashingInstrumentId = crashingInstrumentId;
        }

        @Override
        public boolean supports(PriceSource candidate) {
            return candidate == source;
        }

        @Override
        public Quote fetch(Instrument instrument) {
            if (instrument.id().equals(crashingInstrumentId)) {
                // No message, like the real NullPointerException this reproduces.
                throw new NullPointerException();
            }
            return new Quote(
                    instrument.id(), LocalDate.now(), BigDecimal.TEN, instrument.currency(), source, Instant.now());
        }

        @Override
        public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
            return List.of();
        }
    }

    private static final class RecordingFailurePort implements RecordQuoteFailurePort {
        private final List<UUID> recorded = new ArrayList<>();

        @Override
        public void record(UUID instrumentId, PriceSource source, String message) {
            recorded.add(instrumentId);
        }

        List<UUID> recorded() {
            return recorded;
        }
    }

    private static final class StubLoadInstrumentsPort implements LoadInstrumentsPort {
        private final List<Instrument> instruments;
        private final Set<UUID> deletedSinceRead;

        private StubLoadInstrumentsPort(List<Instrument> instruments) {
            this(instruments, Set.of());
        }

        private StubLoadInstrumentsPort(List<Instrument> instruments, Set<UUID> deletedSinceRead) {
            this.instruments = instruments;
            this.deletedSinceRead = deletedSinceRead;
        }

        @Override
        public List<Instrument> findAll() {
            return instruments;
        }

        @Override
        public Optional<Instrument> findById(UUID id) {
            return instruments.stream()
                    .filter(instrument -> instrument.id().equals(id))
                    .filter(instrument -> !deletedSinceRead.contains(id))
                    .findFirst();
        }

        @Override
        public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
            return instruments.stream()
                    .filter(instrument -> assetClasses.contains(instrument.assetClass()))
                    .toList();
        }
    }

    private static final class NoOpSaveQuotePort implements SaveQuotePort {
        @Override
        public void upsert(Quote quote) {}

        @Override
        public void upsertAll(List<Quote> quotes) {}
    }

    private static final class FailingSaveQuotePort implements SaveQuotePort {
        private final UUID failingInstrumentId;

        private FailingSaveQuotePort(UUID failingInstrumentId) {
            this.failingInstrumentId = failingInstrumentId;
        }

        @Override
        public void upsert(Quote quote) {
            if (quote.instrumentId().equals(failingInstrumentId)) {
                throw new IllegalStateException("simulated write failure");
            }
        }

        @Override
        public void upsertAll(List<Quote> quotes) {}
    }

    private static final class NoOpAnnounceQuotesUseCase implements AnnounceQuotesUseCase {
        @Override
        public void quotesSaved(List<Quote> quotes) {}

        @Override
        public void refreshCompleted(Set<AssetClass> assetClasses, int refreshed, int failed, RefreshTrigger trigger) {}
    }

    /** Records "saved:REF" per announced quote, then "completed:refreshed:failed:trigger", in order. */
    private static final class RecordingAnnounceUseCase implements AnnounceQuotesUseCase {
        private final Map<UUID, String> refsByInstrumentId;
        private final List<String> events = new ArrayList<>();

        private RecordingAnnounceUseCase(Map<UUID, String> refsByInstrumentId) {
            this.refsByInstrumentId = refsByInstrumentId;
        }

        @Override
        public void quotesSaved(List<Quote> quotes) {
            quotes.forEach(quote -> events.add("saved:" + refsByInstrumentId.get(quote.instrumentId())));
        }

        @Override
        public void refreshCompleted(Set<AssetClass> assetClasses, int refreshed, int failed, RefreshTrigger trigger) {
            events.add("completed:" + refreshed + ":" + failed + ":" + trigger);
        }

        List<String> events() {
            return events;
        }
    }
}
