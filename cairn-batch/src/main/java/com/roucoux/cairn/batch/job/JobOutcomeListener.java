package com.roucoux.cairn.batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
class JobOutcomeListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobOutcomeListener.class);

    @Override
    public void afterJob(JobExecution jobExecution) {
        long read = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();
        long written = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        long skipped = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount)
                .sum();
        String line = "job %s %s: read %d, written %d, skipped %d"
                .formatted(
                        jobExecution.getJobInstance().getJobName(), jobExecution.getStatus(), read, written, skipped);
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info(line);
            return;
        }
        log.error(line);
    }
}
