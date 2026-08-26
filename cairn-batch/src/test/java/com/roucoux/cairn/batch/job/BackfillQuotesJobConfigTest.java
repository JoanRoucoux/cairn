package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.BackfillQuotesUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
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
class BackfillQuotesJobConfigTest {

    private static final Instrument CW8 = new Instrument(
            UUID.randomUUID(), "Amundi MSCI World", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "CW8.PA", null);

    @Test
    void readsEveryRefreshableInstrumentWhenNoInstrumentIdIsGiven() throws Exception {
        RecordingInstrumentsPort instruments = new RecordingInstrumentsPort();

        ItemReader<Instrument> reader = new BackfillQuotesJobConfig().instrumentsToBackfillReader(instruments, null);

        assertThat(reader.read()).isEqualTo(CW8);
        assertThat(instruments.requestedClasses()).containsExactlyInAnyOrder(AssetClass.values());
    }

    @Test
    void readsOnlyTheRequestedInstrumentWhenAnInstrumentIdIsGiven() throws Exception {
        RecordingInstrumentsPort instruments = new RecordingInstrumentsPort();

        ItemReader<Instrument> reader = new BackfillQuotesJobConfig()
                .instrumentsToBackfillReader(instruments, CW8.id().toString());

        assertThat(reader.read()).isEqualTo(CW8);
        assertThat(reader.read()).isNull();
    }

    @Test
    void theProcessorDelegatesToTheUseCaseWithTheDefaultFromDateWhenNoneIsGiven() throws Exception {
        RecordingBackfillUseCase useCase = new RecordingBackfillUseCase();

        ItemProcessor<Instrument, Integer> processor =
                new BackfillQuotesJobConfig().backfillQuoteProcessor(useCase, null);
        Integer written = processor.process(CW8);

        assertThat(written).isEqualTo(42);
        assertThat(useCase.calls()).containsExactly(LocalDate.of(2015, 1, 1));
    }

    @Test
    void theProcessorDelegatesToTheUseCaseWithTheGivenFromDate() throws Exception {
        RecordingBackfillUseCase useCase = new RecordingBackfillUseCase();

        ItemProcessor<Instrument, Integer> processor =
                new BackfillQuotesJobConfig().backfillQuoteProcessor(useCase, "2020-06-15");
        processor.process(CW8);

        assertThat(useCase.calls()).containsExactly(LocalDate.of(2020, 6, 15));
    }

    @Test
    void theWriterDoesNothingSincePersistenceAlreadyHappenedInTheUseCase() {
        ItemWriter<Integer> writer = new BackfillQuotesJobConfig().backfilledQuoteCountWriter();

        assertThatCode(() -> writer.write(Chunk.of(120))).doesNotThrowAnyException();
    }

    private static final class RecordingInstrumentsPort implements LoadInstrumentsPort {
        private final List<Set<AssetClass>> requestedClasses = new ArrayList<>();

        @Override
        public List<Instrument> findAll() {
            return List.of(CW8);
        }

        @Override
        public Optional<Instrument> findById(UUID id) {
            return id.equals(CW8.id()) ? Optional.of(CW8) : Optional.empty();
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

    private static final class RecordingBackfillUseCase implements BackfillQuotesUseCase {
        private final List<LocalDate> calls = new ArrayList<>();

        @Override
        public int backfill(Instrument instrument, LocalDate from) {
            calls.add(from);
            return 42;
        }

        List<LocalDate> calls() {
            return calls;
        }
    }
}
