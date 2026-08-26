package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.RefreshReport;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
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
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;

/** The step's glue, without a Spring context: each piece talks to a port or a use case only. */
class RefreshQuotesJobConfigTest {

    private static final Instrument CW8 = new Instrument(
            UUID.randomUUID(), "Amundi MSCI World", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "CW8.PA", null);

    private static final Quote QUOTE_CW8 =
            new Quote(CW8.id(), LocalDate.now(), new BigDecimal("456.78"), "EUR", PriceSource.YAHOO, Instant.now());
    private static final Quote QUOTE_ESE = new Quote(
            UUID.randomUUID(), LocalDate.now(), new BigDecimal("123.45"), "EUR", PriceSource.YAHOO, Instant.now());

    @Test
    void readsOnlyTheRefreshableInstrumentsOfTheRequestedAssetClasses() throws Exception {
        RecordingInstrumentsPort instruments = new RecordingInstrumentsPort();

        ItemReader<Instrument> reader = new RefreshQuotesJobConfig().instrumentReader(instruments, "ETF,CRYPTO");

        assertThat(reader.read()).isNotNull();
        assertThat(instruments.requestedClasses()).containsExactlyInAnyOrder(AssetClass.ETF, AssetClass.CRYPTO);
    }

    @Test
    void theProcessorDelegatesToTheUseCase() throws Exception {
        RecordingRefreshUseCase useCase = new RecordingRefreshUseCase();

        ItemProcessor<Instrument, Quote> processor = new RefreshQuotesJobConfig().refreshQuoteProcessor(useCase);
        processor.process(CW8);

        assertThat(useCase.calls()).containsExactly(CW8.id());
    }

    @Test
    void theWriterUpsertsEveryQuoteOfTheChunk() throws Exception {
        RecordingSaveQuotePort saved = new RecordingSaveQuotePort();

        ItemWriter<Quote> writer = new RefreshQuotesJobConfig().quoteWriter(saved);
        writer.write(Chunk.of(QUOTE_CW8, QUOTE_ESE));

        assertThat(saved.upserted()).hasSize(2);
    }

    private static final class RecordingInstrumentsPort implements LoadInstrumentsPort {
        private final List<Set<AssetClass>> requestedClasses = new ArrayList<>();

        @Override
        public List<Instrument> findAll() {
            return List.of(CW8);
        }

        @Override
        public Optional<Instrument> findById(UUID id) {
            return Optional.of(CW8);
        }

        @Override
        public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
            requestedClasses.add(assetClasses);
            return List.of(CW8);
        }

        Set<AssetClass> requestedClasses() {
            return requestedClasses.getFirst();
        }
    }

    private static final class RecordingRefreshUseCase implements RefreshQuotesUseCase {
        private final List<UUID> calls = new ArrayList<>();

        @Override
        public Quote refresh(Instrument instrument) {
            calls.add(instrument.id());
            return QUOTE_CW8;
        }

        @Override
        public RefreshReport refreshAll(Set<AssetClass> assetClasses) {
            return new RefreshReport(0, 0, List.of());
        }

        List<UUID> calls() {
            return calls;
        }
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
