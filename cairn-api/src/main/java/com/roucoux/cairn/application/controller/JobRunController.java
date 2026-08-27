package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.JobRunRestMapper;
import com.roucoux.cairn.generated.api.JobsApi;
import com.roucoux.cairn.generated.model.JobRunResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter over {@link JobExplorer}: read-only access to the Spring Batch metadata tables
 * written by {@code refreshQuotesJob} and {@code backfillQuotesJob}, not a domain port.
 */
@RestController
class JobRunController implements JobsApi {

    private static final int INSTANCES_PER_JOB_NAME = 100;

    private final JobExplorer jobExplorer;
    private final JobRunRestMapper mapper;

    JobRunController(JobExplorer jobExplorer, JobRunRestMapper mapper) {
        this.jobExplorer = jobExplorer;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<JobRunResponse>> getJobRuns(Integer limit) {
        List<JobRunResponse> runs = jobExplorer.getJobNames().stream()
                .flatMap(jobName -> jobExplorer.findJobInstancesByJobName(jobName, 0, INSTANCES_PER_JOB_NAME).stream())
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .sorted(Comparator.comparing(JobExecution::getStartTime).reversed())
                .limit(limit)
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(runs);
    }
}
