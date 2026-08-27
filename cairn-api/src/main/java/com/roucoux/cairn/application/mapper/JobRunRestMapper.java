package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.generated.model.JobRunResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

/** Maps a Spring Batch {@link JobExecution} to the generated {@link JobRunResponse}. */
@Component
public class JobRunRestMapper {

    public JobRunResponse toResponse(JobExecution execution) {
        JobRunResponse response = new JobRunResponse();
        response.setId(execution.getId());
        response.setJobName(execution.getJobInstance().getJobName());
        response.setStatus(
                JobRunResponse.StatusEnum.valueOf(execution.getStatus().name()));
        response.setStartedAt(toOffsetDateTime(execution.getStartTime()));
        response.setEndedAt(toOffsetDateTime(execution.getEndTime()));
        return response;
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atOffset(ZoneOffset.UTC);
    }
}
