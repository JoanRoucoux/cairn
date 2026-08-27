package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteRecordingServiceTest {

    private static final Instrument LIVRET_A = new Instrument(
            UUID.randomUUID(), "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, "Regulated savings");

    @Test
    void recordsAManualQuoteAndPersistsIt() {
        RecordingSaveQuotePort saveQuote = new RecordingSaveQuotePort();
        QuoteRecordingService service =
                new QuoteRecordingService(new StubLoadInstrumentsPort(List.of(LIVRET_A)), saveQuote);

        Quote quote = service.record(LIVRET_A.id(), LocalDate.of(2026, 8, 20), new BigDecimal("57.48"));

        assertThat(quote.price()).isEqualByComparingTo("57.48");
        assertThat(quote.source()).isEqualTo(PriceSource.MANUAL);
        assertThat(quote.currency()).isEqualTo("EUR");
        assertThat(saveQuote.saved()).containsExactly(quote);
    }

    @Test
    void failsLoudlyWhenTheInstrumentIsUnknown() {
        QuoteRecordingService service =
                new QuoteRecordingService(new StubLoadInstrumentsPort(List.of()), new RecordingSaveQuotePort());

        assertThatThrownBy(() -> service.record(UUID.randomUUID(), LocalDate.now(), BigDecimal.TEN))
                .isInstanceOf(NotFoundException.class);
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

    private static final class RecordingSaveQuotePort implements SaveQuotePort {
        private final List<Quote> saved = new ArrayList<>();

        @Override
        public void upsert(Quote quote) {
            saved.add(quote);
        }

        @Override
        public void upsertAll(List<Quote> quotes) {
            saved.addAll(quotes);
        }

        List<Quote> saved() {
            return saved;
        }
    }
}
