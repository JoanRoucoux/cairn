package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Position;
import java.math.BigDecimal;
import java.util.UUID;
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

/**
 * Runs against a real PostgreSQL (Testcontainers). This module has no dependency on the schema
 * module (only the api module's ApplicationIT does, to validate against the real Liquibase
 * changelog) — this slice generates its own throwaway schema from the JPA mapping instead, kept
 * fast and self-contained.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(PositionPersistenceAdapter.class)
@Testcontainers
class PositionPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private PositionPersistenceAdapter adapter;

    @Test
    void savesAndReadsBackAPosition() {
        Position position = Position.open("US0378331005", new BigDecimal("10.0000"), new BigDecimal("123.4500"));

        adapter.save(position);

        assertThat(adapter.findById(position.id())).hasValueSatisfying(found -> {
            assertThat(found.isin()).isEqualTo(position.isin());
            assertThat(found.quantity()).isEqualByComparingTo(position.quantity());
            assertThat(found.price()).isEqualByComparingTo(position.price());
            assertThat(found.status()).isEqualTo(position.status());
        });
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }
}
