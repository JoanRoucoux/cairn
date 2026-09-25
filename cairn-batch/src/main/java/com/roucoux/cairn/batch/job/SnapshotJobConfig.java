package com.roucoux.cairn.batch.job;

import com.roucoux.cairn.domain.port.in.ComputeSnapshotUseCase;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class SnapshotJobConfig {

    @Bean
    Job snapshotJob(JobRepository jobRepository, Step snapshotStep) {
        return new JobBuilder("snapshotJob", jobRepository).start(snapshotStep).build();
    }

    @Bean
    Step snapshotStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, Tasklet snapshotTasklet) {
        return new StepBuilder("snapshotStep", jobRepository)
                .tasklet(snapshotTasklet, transactionManager)
                .build();
    }

    @Bean
    Tasklet snapshotTasklet(ComputeSnapshotUseCase computeSnapshot) {
        return (contribution, chunkContext) -> {
            computeSnapshot.compute();
            return RepeatStatus.FINISHED;
        };
    }
}
