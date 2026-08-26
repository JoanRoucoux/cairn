package com.roucoux.cairn.batch.job;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.port.in.BackfillQuotesUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Fills in historical quote gaps through the domain's inbound port: one instrument, or every
 * refreshable instrument when {@code instrumentId} is omitted, from the {@code from} date forward.
 * The use case itself persists the fetched history, so the writer has nothing left to do.
 */
@Configuration(proxyBeanMethods = false)
class BackfillQuotesJobConfig {

    private static final int CHUNK_SIZE = 5;
    private static final LocalDate DEFAULT_FROM = LocalDate.of(2015, 1, 1);

    @Bean
    Job backfillQuotesJob(JobRepository jobRepository, Step backfillQuotesStep) {
        return new JobBuilder("backfillQuotesJob", jobRepository)
                .start(backfillQuotesStep)
                .build();
    }

    @Bean
    Step backfillQuotesStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<Instrument> instrumentsToBackfillReader,
            ItemProcessor<Instrument, Integer> backfillQuoteProcessor,
            ItemWriter<Integer> backfilledQuoteCountWriter) {
        return new StepBuilder("backfillQuotesStep", jobRepository)
                .<Instrument, Integer>chunk(CHUNK_SIZE, transactionManager)
                .reader(instrumentsToBackfillReader)
                .processor(backfillQuoteProcessor)
                .writer(backfilledQuoteCountWriter)
                .build();
    }

    @Bean
    @StepScope
    ItemReader<Instrument> instrumentsToBackfillReader(
            LoadInstrumentsPort loadInstruments, @Value("#{jobParameters['instrumentId']}") String instrumentId) {
        List<Instrument> instruments = (instrumentId == null || instrumentId.isBlank())
                ? loadInstruments.findRefreshable(Set.of(AssetClass.values()))
                : loadInstruments
                        .findById(UUID.fromString(instrumentId))
                        .map(List::of)
                        .orElseGet(List::of);
        return new ListItemReader<>(instruments);
    }

    @Bean
    @StepScope
    ItemProcessor<Instrument, Integer> backfillQuoteProcessor(
            BackfillQuotesUseCase backfillQuotes, @Value("#{jobParameters['from']}") String from) {
        LocalDate fromDate = (from == null || from.isBlank()) ? DEFAULT_FROM : LocalDate.parse(from);
        return instrument -> backfillQuotes.backfill(instrument, fromDate);
    }

    @Bean
    ItemWriter<Integer> backfilledQuoteCountWriter() {
        return chunk -> {};
    }
}
