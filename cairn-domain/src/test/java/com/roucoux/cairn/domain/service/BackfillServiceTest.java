package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BackfillServiceTest {

    private static final Instrument CW8 = new Instrument(
            UUID.randomUUID(), "Amundi MSCI World", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "CW8.PA", null);
    private static final Instrument LIVRET_A = new Instrument(
            UUID.randomUUID(),
            "Livret A",
            null,
            "EUR",
            AssetClass.CASH,
            PriceSource.MANUAL,
            null,
            "Livret d'epargne reglementee");

    @Test
    void writesEveryHistoricalQuoteReturnedByTheSource() {
        RecordingSaveQuotePort saved = new RecordingSaveQuotePort();
        BackfillService service = new BackfillService(List.of(historyPort(120)), saved);

        int written = service.backfill(CW8, LocalDate.of(2015, 1, 1));

        assertThat(written).isEqualTo(120);
        assertThat(saved.upserted()).hasSize(120);
    }

    @Test
    void skipsAManuallyPricedInstrument() {
        RecordingSaveQuotePort saved = new RecordingSaveQuotePort();
        BackfillService service = new BackfillService(List.of(historyPort(120)), saved);

        assertThat(service.backfill(LIVRET_A, LocalDate.of(2015, 1, 1))).isZero();
        assertThat(saved.upserted()).isEmpty();
    }

    @Test
    void raisesWhenNoAdapterSupportsTheSource() {
        BackfillService service = new BackfillService(List.of(), new RecordingSaveQuotePort());

        assertThatThrownBy(() -> service.backfill(CW8, LocalDate.of(2015, 1, 1)))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    private static FetchQuotePort historyPort(int size) {
        List<Quote> history = IntStream.range(0, size)
                .mapToObj(i -> new Quote(
                        CW8.id(),
                        LocalDate.of(2015, 1, 1).plusDays(i),
                        new BigDecimal("100.00"),
                        "EUR",
                        PriceSource.YAHOO,
                        Instant.now()))
                .toList();
        return new FetchQuotePort() {
            @Override
            public boolean supports(PriceSource source) {
                return source == PriceSource.YAHOO;
            }

            @Override
            public Quote fetch(Instrument instrument) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
                return history;
            }
        };
    }

    private static final class RecordingSaveQuotePort implements SaveQuotePort {
        private final List<Quote> upserted = new ArrayList<>();

        @Override
        public void upsert(Quote quote) {
            upserted.add(quote);
        }

        @Override
        public void upsertAll(List<Quote> quotes) {
            upserted.addAll(quotes);
        }

        List<Quote> upserted() {
            return upserted;
        }
    }
}
