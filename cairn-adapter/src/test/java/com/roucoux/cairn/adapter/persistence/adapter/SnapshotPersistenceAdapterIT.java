package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.adapter.persistence.repository.SnapshotBreakdownJpaRepository;
import com.roucoux.cairn.domain.model.Snapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.hikari.connection-timeout=250"})
@Import({SnapshotPersistenceAdapter.class, SnapshotPersistenceAdapterIT.ClockConfig.class})
class SnapshotPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    SnapshotPersistenceAdapter snapshots;

    @Autowired
    SnapshotBreakdownJpaRepository breakdowns;

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-24T22:30:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void writesTheTotalAndBothVentilations() {
        Snapshot snapshot = new Snapshot(
                LocalDate.of(2026, 9, 24),
                new BigDecimal("150"),
                Map.of("CTO", new BigDecimal("100"), "PEA", new BigDecimal("50")),
                Map.of("EQUITY", new BigDecimal("100"), "ETF", new BigDecimal("50")));

        snapshots.save(snapshot);

        assertThat(snapshots.findBetween(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 24)))
                .singleElement()
                .satisfies(read -> assertThat(read.totalEur()).isEqualByComparingTo("150"));
        assertThat(breakdowns.count()).isEqualTo(4);
    }

    @Test
    void rerunningTheSameDayReplacesTheTotalAndLeavesNoOrphanedVentilation() {
        snapshots.save(new Snapshot(
                LocalDate.of(2026, 9, 24),
                new BigDecimal("150"),
                Map.of("CTO", new BigDecimal("100"), "PEA", new BigDecimal("50")),
                Map.of("EQUITY", new BigDecimal("100"), "ETF", new BigDecimal("50"))));

        snapshots.save(new Snapshot(
                LocalDate.of(2026, 9, 24),
                new BigDecimal("200"),
                Map.of("CTO", new BigDecimal("200")),
                Map.of("EQUITY", new BigDecimal("200"))));

        assertThat(snapshots.findBetween(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 24)))
                .singleElement()
                .satisfies(read -> assertThat(read.totalEur()).isEqualByComparingTo("200"));
        assertThat(breakdowns.count()).isEqualTo(2);
    }

    @Test
    void readsTheSnapshotWithoutItsVentilations() {
        snapshots.save(new Snapshot(
                LocalDate.of(2026, 9, 24),
                new BigDecimal("150"),
                Map.of("CTO", new BigDecimal("150")),
                Map.of("EQUITY", new BigDecimal("150"))));

        Snapshot read = snapshots
                .findBetween(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 24))
                .getFirst();

        assertThat(read.byAccountType()).isEmpty();
        assertThat(read.byAssetClass()).isEmpty();
    }
}
