package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.JobRunRestMapper;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobRunController.class)
@Import({WebAuthnConfig.class, JobRunRestMapper.class})
class JobRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobExplorer jobExplorer;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void listsRecentRunsMostRecentFirst() throws Exception {
        when(jobExplorer.getJobNames()).thenReturn(List.of("refreshQuotesJob"));
        JobInstance instance = new JobInstance(1L, "refreshQuotesJob");
        JobExecution completed = new JobExecution(10L, instance, new JobParameters());
        completed.setStatus(BatchStatus.COMPLETED);
        completed.setStartTime(LocalDateTime.of(2026, 8, 21, 3, 0));
        completed.setEndTime(LocalDateTime.of(2026, 8, 21, 3, 2));
        when(jobExplorer.findJobInstancesByJobName(eq("refreshQuotesJob"), anyInt(), anyInt()))
                .thenReturn(List.of(instance));
        when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(completed));

        mockMvc.perform(get("/jobs/runs").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobName").value("refreshQuotesJob"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].endedAt").exists());
    }

    @Test
    void leavesEndedAtAbsentForAStillRunningJob() throws Exception {
        when(jobExplorer.getJobNames()).thenReturn(List.of("backfillQuotesJob"));
        JobInstance instance = new JobInstance(2L, "backfillQuotesJob");
        JobExecution running = new JobExecution(11L, instance, new JobParameters());
        running.setStatus(BatchStatus.STARTED);
        running.setStartTime(LocalDateTime.of(2026, 8, 21, 4, 0));
        when(jobExplorer.findJobInstancesByJobName(eq("backfillQuotesJob"), anyInt(), anyInt()))
                .thenReturn(List.of(instance));
        when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(running));

        mockMvc.perform(get("/jobs/runs").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endedAt").doesNotExist());
    }

    @Test
    void limitsAndOrdersAcrossMultipleJobNames() throws Exception {
        when(jobExplorer.getJobNames()).thenReturn(List.of("refreshQuotesJob", "backfillQuotesJob"));

        JobInstance refreshInstance = new JobInstance(1L, "refreshQuotesJob");
        JobExecution older = new JobExecution(10L, refreshInstance, new JobParameters());
        older.setStatus(BatchStatus.COMPLETED);
        older.setStartTime(LocalDateTime.of(2026, 8, 20, 3, 0));
        older.setEndTime(LocalDateTime.of(2026, 8, 20, 3, 2));

        JobInstance backfillInstance = new JobInstance(2L, "backfillQuotesJob");
        JobExecution newer = new JobExecution(11L, backfillInstance, new JobParameters());
        newer.setStatus(BatchStatus.FAILED);
        newer.setStartTime(LocalDateTime.of(2026, 8, 22, 3, 0));
        newer.setEndTime(LocalDateTime.of(2026, 8, 22, 3, 2));

        when(jobExplorer.findJobInstancesByJobName(eq("refreshQuotesJob"), anyInt(), anyInt()))
                .thenReturn(List.of(refreshInstance));
        when(jobExplorer.findJobInstancesByJobName(eq("backfillQuotesJob"), anyInt(), anyInt()))
                .thenReturn(List.of(backfillInstance));
        when(jobExplorer.getJobExecutions(refreshInstance)).thenReturn(List.of(older));
        when(jobExplorer.getJobExecutions(backfillInstance)).thenReturn(List.of(newer));

        mockMvc.perform(get("/jobs/runs").param("limit", "1").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobName").value("backfillQuotesJob"));
    }

    @Test
    void refusesAnUnauthenticatedCall() throws Exception {
        mockMvc.perform(get("/jobs/runs")).andExpect(status().isUnauthorized());
    }
}
