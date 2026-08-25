package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private static final Instrument WPEA = new Instrument(
            UUID.randomUUID(), "Amundi PEA S&P 500", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "WPEA.PA", null);
    private static final Instrument CW8 = new Instrument(
            UUID.randomUUID(), "Amundi MSCI World", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "CW8.PA", null);

    @Test
    void routesAnInstrumentToTheAdapterThatSupportsItsSource() {
        RecordingPort yahoo = new RecordingPort(PriceSource.YAHOO);
        RecordingPort coinGecko = new RecordingPort(PriceSource.COINGECKO);
        QuoteRefreshService service = service(List.of(yahoo, coinGecko), List.of(ETHEREUM));

        service.refreshAll(Set.of(AssetClass.CRYPTO));

        assertThat(coinGecko.calls()).containsExactly(ETHEREUM.id());
        assertThat(yahoo.calls()).isEmpty();
    }

    @Test
    void neverRefreshesAManuallyPricedInstrument() {
        RecordingPort yahoo = new RecordingPort(PriceSource.YAHOO);
        QuoteRefreshService service = service(List.of(yahoo), List.of(LIVRET_A));

        RefreshReport report = service.refreshAll(Set.of(AssetClass.CASH));

        assertThat(yahoo.calls()).isEmpty();
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.refreshed()).isZero();
    }

    @Test
    void oneFailingInstrumentDoesNotStopTheOthers() {
        FetchQuotePort failing = new FailingPort(PriceSource.YAHOO, WPEA.id());
        QuoteRefreshService service = service(List.of(failing), List.of(WPEA, CW8));

        RefreshReport report = service.refreshAll(Set.of(AssetClass.ETF));

        assertThat(report.refreshed()).isEqualTo(1);
        assertThat(report.failures())
                .singleElement()
                .satisfies(failure -> assertThat(failure.instrumentId()).isEqualTo(WPEA.id()));
    }

    @Test
    void recordsEveryFailureThroughThePort() {
        RecordingFailurePort failures = new RecordingFailurePort();
        QuoteRefreshService service =
                service(List.of(new FailingPort(PriceSource.YAHOO, WPEA.id())), List.of(WPEA), failures);

        service.refreshAll(Set.of(AssetClass.ETF));

        assertThat(failures.recorded()).hasSize(1);
    }

    @Test
    void failsLoudlyWhenNoAdapterSupportsTheSource() {
        QuoteRefreshService service = service(List.of(), List.of(CW8));

        assertThatThrownBy(() -> service.refresh(CW8))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("YAHOO");
    }

    private static QuoteRefreshService service(List<FetchQuotePort> fetchers, List<Instrument> instruments) {
        return service(fetchers, instruments, new RecordingFailurePort());
    }

    private static QuoteRefreshService service(
            List<FetchQuotePort> fetchers, List<Instrument> instruments, RecordQuoteFailurePort failurePort) {
        return new QuoteRefreshService(
                fetchers, new StubLoadInstrumentsPort(instruments), new NoOpSaveQuotePort(), failurePort);
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

        private StubLoadInstrumentsPort(List<Instrument> instruments) {
            this.instruments = instruments;
        }

        @Override
        public List<Instrument> findAll() {
            return instruments;
        }

        @Override
        public Optional<Instrument> findById(UUID id) {
            return instruments.stream()
                    .filter(instrument -> instrument.id().equals(id))
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
}
