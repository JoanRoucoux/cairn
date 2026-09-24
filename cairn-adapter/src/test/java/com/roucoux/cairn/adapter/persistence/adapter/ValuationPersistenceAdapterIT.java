package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.IntradayValuation;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
@Import(ValuationPersistenceAdapter.class)
class ValuationPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ValuationPersistenceAdapter valuations;

    @Test
    void replacesTheValuationOfAMinuteInsteadOfDuplicatingIt() {
        Instant at = Instant.parse("2026-09-24T13:47:00Z");
        valuations.upsert(new IntradayValuation(at, new BigDecimal("25950.0000")));

        valuations.upsert(new IntradayValuation(at, new BigDecimal("26000.0000")));

        assertThat(valuations.findBetween(at, at))
                .hasSize(1)
                .extracting(IntradayValuation::totalEur)
                .containsExactly(new BigDecimal("26000.0000"));
    }

    @Test
    void findsPointsSortedAscendingAndBoundedByTheInterval() {
        Instant first = Instant.parse("2026-09-24T13:45:00Z");
        Instant second = Instant.parse("2026-09-24T13:46:00Z");
        Instant outOfRange = Instant.parse("2026-09-24T13:50:00Z");
        valuations.upsert(new IntradayValuation(second, new BigDecimal("100")));
        valuations.upsert(new IntradayValuation(first, new BigDecimal("90")));
        valuations.upsert(new IntradayValuation(outOfRange, new BigDecimal("200")));

        assertThat(valuations.findBetween(first, second))
                .extracting(IntradayValuation::at)
                .containsExactly(first, second);
    }

    @Test
    void deletesOnlyPointsStrictlyBeforeTheCutoff() {
        Instant kept = Instant.parse("2026-09-24T13:47:00Z");
        Instant removed = Instant.parse("2026-08-25T13:47:00Z");
        valuations.upsert(new IntradayValuation(kept, new BigDecimal("100")));
        valuations.upsert(new IntradayValuation(removed, new BigDecimal("50")));

        valuations.deleteBefore(kept);

        assertThat(valuations.findBetween(Instant.EPOCH, kept))
                .extracting(IntradayValuation::at)
                .containsExactly(kept);
    }
}
