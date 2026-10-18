package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.QuoteFailureJpaRepository;
import com.roucoux.cairn.domain.model.PriceSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.hikari.connection-timeout=250"})
@Import(QuoteFailurePersistenceAdapter.class)
class QuoteFailurePersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    QuoteFailurePersistenceAdapter failures;

    @Autowired
    QuoteFailureJpaRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void recordsAMessageLongerThanTheColumnTruncatedInsteadOfFailing() {
        failures.record(UUID.randomUUID(), PriceSource.YAHOO, "x".repeat(2000));
        repository.flush();

        assertThat(jdbc.queryForObject("select length(message) from quote_failures", Integer.class))
                .isEqualTo(500);
    }

    @Test
    void keepsAShortMessageAsIs() {
        failures.record(UUID.randomUUID(), PriceSource.YAHOO, "boom");
        repository.flush();

        assertThat(jdbc.queryForObject("select message from quote_failures", String.class))
                .isEqualTo("boom");
    }

    @Test
    void deletesFailuresOlderThanTheCutoffAndKeepsTheOthers() {
        failures.record(UUID.randomUUID(), PriceSource.YAHOO, "old");
        failures.record(UUID.randomUUID(), PriceSource.YAHOO, "recent");
        repository.flush();
        jdbc.update(
                "update quote_failures set occurred_at = ? where message = 'old'",
                Timestamp.from(Instant.now().minus(Duration.ofDays(100))));

        failures.deleteBefore(Instant.now().minus(Duration.ofDays(90)));

        assertThat(jdbc.queryForList("select message from quote_failures", String.class))
                .containsExactly("recent");
    }
}
