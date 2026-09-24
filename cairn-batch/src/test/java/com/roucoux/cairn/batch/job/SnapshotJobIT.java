package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.SnapshotBreakdownJpaRepository;
import com.roucoux.cairn.adapter.persistence.repository.SnapshotJpaRepository;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the real job against a real PostgreSQL migrated with the schema module's changelog: seeds
 * one account and one valued holding, then checks the total and both ventilations it writes.
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            "spring.batch.job.enabled=false"
        })
@Testcontainers
class SnapshotJobIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private SaveAccountPort accounts;

    @Autowired
    private SaveInstrumentPort instruments;

    @Autowired
    private SaveHoldingPort holdings;

    @Autowired
    private SaveQuotePort quotes;

    @Autowired
    private SnapshotJpaRepository snapshots;

    @Autowired
    private SnapshotBreakdownJpaRepository breakdowns;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @TestConfiguration
    static class JobLauncherTestUtilsConfig {

        @Bean
        JobLauncherTestUtils jobLauncherTestUtils(
                JobLauncher jobLauncher, JobRepository jobRepository, Job snapshotJob) {
            JobLauncherTestUtils utils = new JobLauncherTestUtils();
            utils.setJobLauncher(jobLauncher);
            utils.setJobRepository(jobRepository);
            utils.setJob(snapshotJob);
            return utils;
        }
    }

    private JobParameters parameters() {
        return new JobParametersBuilder()
                .addLong("run", System.currentTimeMillis())
                .toJobParameters();
    }

    @Test
    void recordsTonightsSnapshotWithBothVentilations() throws Exception {
        Account account = accounts.save(new Account(UUID.randomUUID(), "Test", AccountType.CTO, "Test institution"));
        Instrument instrument = instruments.save(new Instrument(
                UUID.randomUUID(),
                "Amundi MSCI World",
                null,
                "EUR",
                AssetClass.ETF,
                PriceSource.YAHOO,
                "ETF.PA",
                null));
        holdings.save(new Holding(UUID.randomUUID(), account.id(), instrument.id(), new BigDecimal("10"), null));
        quotes.upsert(new Quote(
                instrument.id(), LocalDate.now(), new BigDecimal("100.00"), "EUR", PriceSource.YAHOO, Instant.now()));

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters());

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(snapshots.count()).isEqualTo(1);
        assertThat(snapshots.findAll().getFirst().toDomainWithoutBreakdowns().totalEur())
                .isEqualByComparingTo("1000.00");
        assertThat(breakdowns.count()).isEqualTo(2);
    }
}
