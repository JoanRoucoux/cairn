package com.roucoux.cairn.batch.job;

import static java.util.stream.Collectors.toSet;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.RefreshQuotesUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
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
 * Reads the refreshable instruments of the requested asset classes, refreshes each one through the
 * domain's inbound port, writes the resulting quotes back through an outbound port. One failing
 * source is skipped rather than stopping the run: the last known price stays in place and the
 * failure is recorded, instead of the whole line disappearing the way it would in a spreadsheet.
 */
@Configuration(proxyBeanMethods = false)
class RefreshQuotesJobConfig {

    private static final int CHUNK_SIZE = 20;

    @Bean
    Job refreshQuotesJob(JobRepository jobRepository, Step refreshQuotesStep) {
        return new JobBuilder("refreshQuotesJob", jobRepository)
                .start(refreshQuotesStep)
                .build();
    }

    @Bean
    Step refreshQuotesStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<Instrument> instrumentReader,
            ItemProcessor<Instrument, Quote> refreshQuoteProcessor,
            ItemWriter<Quote> quoteWriter,
            QuoteFailureSkipListener skipListener) {
        return new StepBuilder("refreshQuotesStep", jobRepository)
                .<Instrument, Quote>chunk(CHUNK_SIZE, transactionManager)
                .reader(instrumentReader)
                .processor(refreshQuoteProcessor)
                .writer(quoteWriter)
                .faultTolerant()
                .skip(MarketDataUnavailableException.class)
                .skipLimit(10)
                .listener(skipListener)
                .build();
    }

    @Bean
    @StepScope
    ItemReader<Instrument> instrumentReader(
            LoadInstrumentsPort loadInstruments, @Value("#{jobParameters['assetClasses']}") String assetClasses) {
        Set<AssetClass> classes = Arrays.stream(assetClasses.split(","))
                .map(String::trim)
                .map(AssetClass::valueOf)
                .collect(toSet());
        return new ListItemReader<>(loadInstruments.findRefreshable(classes));
    }

    @Bean
    ItemProcessor<Instrument, Quote> refreshQuoteProcessor(RefreshQuotesUseCase refreshQuotes) {
        return refreshQuotes::refresh;
    }

    @Bean
    ItemWriter<Quote> quoteWriter(SaveQuotePort saveQuote) {
        return chunk -> saveQuote.upsertAll(new ArrayList<>(chunk.getItems()));
    }
}
