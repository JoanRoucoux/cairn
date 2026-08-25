package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.out.LoadPositionPort;
import com.roucoux.cairn.domain.port.out.MarketDataPort;
import com.roucoux.cairn.domain.port.out.SavePositionPort;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the real job against a real PostgreSQL migrated with the schema module's changelog (test
 * scope only — the batch never migrates a database itself). The market data provider is mocked at
 * the port: the client adapter has its own test.
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            // The job is launched explicitly below, not by Spring Boot's startup runner.
            "spring.batch.job.enabled=false"
        })
@Testcontainers
class RevaluePositionsJobIT {

    private static final String ISIN = "US0378331005";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @MockitoBean
    private MarketDataPort marketDataPort;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job revaluePositionsJob;

    @Autowired
    private SavePositionPort savePosition;

    @Autowired
    private LoadPositionPort loadPosition;

    @Test
    void revaluesEveryOpenPositionAtTheCurrentPrice() throws Exception {
        Position position =
                savePosition.save(Position.open(ISIN, new BigDecimal("10.0000"), new BigDecimal("100.0000")));
        when(marketDataPort.currentPrice(ISIN)).thenReturn(Optional.of(new BigDecimal("123.4500")));

        JobExecution execution = jobLauncher.run(
                revaluePositionsJob,
                new JobParametersBuilder()
                        .addLong("run", System.currentTimeMillis())
                        .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(loadPosition.findById(position.id()))
                .hasValueSatisfying(found -> assertThat(found.price()).isEqualByComparingTo(new BigDecimal("123.45")));
    }
}
