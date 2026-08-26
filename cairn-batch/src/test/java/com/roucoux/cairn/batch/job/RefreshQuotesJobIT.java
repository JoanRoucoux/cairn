package com.roucoux.cairn.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.roucoux.cairn.adapter.client.adapter.CoinGeckoQuoteAdapter;
import com.roucoux.cairn.adapter.client.adapter.YahooQuoteAdapter;
import com.roucoux.cairn.adapter.persistence.repository.InstrumentJpaRepository;
import com.roucoux.cairn.adapter.persistence.repository.QuoteFailureJpaRepository;
import com.roucoux.cairn.adapter.persistence.repository.QuoteJpaRepository;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the real job against a real PostgreSQL migrated with the schema module's changelog. Yahoo
 * and CoinGecko are mocked at the adapter: one Yahoo instrument fails so the fault-tolerant step
 * proves it does not stop the run, and a CoinGecko-sourced instrument exercises the second real
 * source end to end. {@code @MockitoBean} replaces the adapter bean outright, so this does not
 * exercise {@code BatchDomainConfig}'s request-to-step rescoping itself — that is covered by
 * {@code BatchDomainConfigTest}, against the real target bean definition.
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.liquibase.change-log=classpath:db/changelog/changelog-master.xml",
            // The job is launched explicitly below, not by Spring Boot's startup runner.
            "spring.batch.job.enabled=false"
        })
@Testcontainers
class RefreshQuotesJobIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @MockitoBean
    private YahooQuoteAdapter yahooQuoteAdapter;

    @MockitoBean
    private CoinGeckoQuoteAdapter coinGeckoQuoteAdapter;

    @Autowired
    private SaveInstrumentPort instruments;

    @Autowired
    private QuoteJpaRepository quotes;

    @Autowired
    private QuoteFailureJpaRepository failures;

    @Autowired
    private InstrumentJpaRepository instrumentRepository;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @BeforeEach
    void clearPreviousRuns() {
        instrumentRepository.deleteAll();
    }

    @TestConfiguration
    static class JobLauncherTestUtilsConfig {

        @Bean
        JobLauncherTestUtils jobLauncherTestUtils(
                JobLauncher jobLauncher, JobRepository jobRepository, Job refreshQuotesJob) {
            JobLauncherTestUtils utils = new JobLauncherTestUtils();
            utils.setJobLauncher(jobLauncher);
            utils.setJobRepository(jobRepository);
            utils.setJob(refreshQuotesJob);
            return utils;
        }
    }

    private static Instrument etf(String name, String sourceRef) {
        return new Instrument(
                UUID.randomUUID(),
                name,
                null,
                "EUR",
                AssetClass.ETF,
                PriceSource.YAHOO,
                sourceRef + "-" + UUID.randomUUID(),
                null);
    }

    private static Instrument crypto(String name, String sourceRef) {
        return new Instrument(
                UUID.randomUUID(),
                name,
                null,
                "EUR",
                AssetClass.CRYPTO,
                PriceSource.COINGECKO,
                sourceRef + "-" + UUID.randomUUID(),
                null);
    }

    private static Quote quoteOf(Instrument instrument, String price) {
        return new Quote(
                instrument.id(), LocalDate.now(), new BigDecimal(price), "EUR", PriceSource.YAHOO, Instant.now());
    }

    private static Quote coinGeckoQuoteOf(Instrument instrument, String price) {
        return new Quote(
                instrument.id(), LocalDate.now(), new BigDecimal(price), "EUR", PriceSource.COINGECKO, Instant.now());
    }

    private JobParameters parameters(String assetClasses) {
        return new JobParametersBuilder()
                .addString("assetClasses", assetClasses)
                .addLong("run", System.currentTimeMillis())
                .toJobParameters();
    }

    private JobParameters parameters(String assetClasses, String key, String value) {
        return new JobParametersBuilder()
                .addString("assetClasses", assetClasses)
                .addString(key, value)
                .toJobParameters();
    }

    private void givenThreeInstrumentsOfWhichOneFails() {
        Instrument working1 = instruments.save(etf("Amundi MSCI World", "CW8.PA"));
        Instrument working2 = instruments.save(etf("Amundi PEA S&P 500", "WPEA.PA"));
        Instrument failing = instruments.save(etf("Lyxor CAC 40", "CAC.PA"));

        when(yahooQuoteAdapter.supports(PriceSource.YAHOO)).thenReturn(true);
        when(yahooQuoteAdapter.fetch(working1)).thenReturn(quoteOf(working1, "456.78"));
        when(yahooQuoteAdapter.fetch(working2)).thenReturn(quoteOf(working2, "123.45"));
        when(yahooQuoteAdapter.fetch(failing)).thenThrow(new MarketDataUnavailableException("simulated timeout"));
    }

    private void givenTwoWorkingInstruments() {
        Instrument working1 = instruments.save(etf("Amundi MSCI World", "CW8.PA"));
        Instrument working2 = instruments.save(etf("Amundi PEA S&P 500", "WPEA.PA"));

        when(yahooQuoteAdapter.supports(PriceSource.YAHOO)).thenReturn(true);
        when(yahooQuoteAdapter.fetch(working1)).thenReturn(quoteOf(working1, "456.78"));
        when(yahooQuoteAdapter.fetch(working2)).thenReturn(quoteOf(working2, "123.45"));
    }

    private void givenOneCryptoInstrument() {
        Instrument bitcoin = instruments.save(crypto("Bitcoin", "bitcoin"));

        when(coinGeckoQuoteAdapter.supports(PriceSource.COINGECKO)).thenReturn(true);
        when(coinGeckoQuoteAdapter.fetch(bitcoin)).thenReturn(coinGeckoQuoteOf(bitcoin, "54321.00"));
    }

    @Test
    void refreshesACryptoInstrumentThroughTheStepScopedCoinGeckoAdapter() throws Exception {
        givenOneCryptoInstrument();

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("CRYPTO"));

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(quotes.countAll()).isEqualTo(1);
    }

    @Test
    void oneFailingSourceDoesNotStopTheRun() throws Exception {
        givenThreeInstrumentsOfWhichOneFails();

        JobExecution execution = jobLauncherTestUtils.launchJob(parameters("ETF"));

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(quotes.countAll()).isEqualTo(2);
        assertThat(failures.countAll()).isEqualTo(1);
    }

    @Test
    void runningTheJobTwiceOnTheSameDayLeavesOneQuotePerInstrument() throws Exception {
        givenTwoWorkingInstruments();

        jobLauncherTestUtils.launchJob(parameters("ETF"));
        jobLauncherTestUtils.launchJob(parameters("ETF", "runDate", "2026-08-22"));

        assertThat(quotes.countAll()).isEqualTo(2);
    }
}
