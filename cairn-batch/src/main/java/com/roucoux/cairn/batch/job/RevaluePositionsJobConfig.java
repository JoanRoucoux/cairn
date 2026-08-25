package com.roucoux.cairn.batch.job;

import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.in.RevaluePositionUseCase;
import com.roucoux.cairn.domain.port.out.LoadOpenPositionsPort;
import com.roucoux.cairn.domain.port.out.SavePositionPort;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Reads the open positions through an outbound port, revalues each one through the domain's
 * inbound port, writes them back through another outbound port. The job knows the ports only —
 * never an adapter, never a domain service implementation.
 */
@Configuration(proxyBeanMethods = false)
class RevaluePositionsJobConfig {

    private static final int CHUNK_SIZE = 50;

    @Bean
    Job revaluePositionsJob(JobRepository jobRepository, Step revaluePositionsStep) {
        return new JobBuilder("revaluePositionsJob", jobRepository)
                .start(revaluePositionsStep)
                .build();
    }

    @Bean
    Step revaluePositionsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<Position> openPositionsReader,
            ItemProcessor<Position, Position> revaluePositionProcessor,
            ItemWriter<Position> positionWriter) {
        return new StepBuilder("revaluePositionsStep", jobRepository)
                .<Position, Position>chunk(CHUNK_SIZE, transactionManager)
                .reader(openPositionsReader)
                .processor(revaluePositionProcessor)
                .writer(positionWriter)
                .build();
    }

    @Bean
    @StepScope
    ItemReader<Position> openPositionsReader(LoadOpenPositionsPort loadOpenPositions) {
        return new ListItemReader<>(loadOpenPositions.findOpen());
    }

    @Bean
    ItemProcessor<Position, Position> revaluePositionProcessor(RevaluePositionUseCase revaluePosition) {
        return revaluePosition::revalue;
    }

    @Bean
    ItemWriter<Position> positionWriter(SavePositionPort savePosition) {
        return chunk -> chunk.forEach(savePosition::save);
    }
}
