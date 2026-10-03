package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;

class JobOutcomeListenerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(JobOutcomeListener.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setAdditive(true);
    }

    @Test
    void logsTheJobOutcomeWithItsCounts() {
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution("refreshQuotesJob", 1L, 1L);
        StepExecution step = MetaDataInstanceFactory.createStepExecution(jobExecution, "refreshQuotesStep", 1L);
        jobExecution.addStepExecution(step);
        step.setReadCount(5);
        step.setWriteCount(4);
        step.setProcessSkipCount(1);
        jobExecution.setStatus(BatchStatus.COMPLETED);

        new JobOutcomeListener().afterJob(jobExecution);

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("job refreshQuotesJob COMPLETED: read 5, written 4, skipped 1");
        });
    }

    @Test
    void logsAFailedJobOnceAtErrorWithoutStack() {
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution("snapshotJob", 2L, 2L);
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(new IllegalStateException("boom"));
        jobExecution.addFailureException(new IllegalArgumentException("bang"));

        new JobOutcomeListener().afterJob(jobExecution);

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).isEqualTo("job snapshotJob FAILED: read 0, written 0, skipped 0");
        });
    }
}
