package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Position;
import java.math.BigDecimal;
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
 * Covers the bulk read the batch module relies on. Kept apart from
 * {@link PositionPersistenceAdapterIT} so that an application generated without the batch drops
 * this file along with the adapter it exercises.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({OpenPositionsPersistenceAdapter.class, PositionPersistenceAdapter.class})
@Testcontainers
class OpenPositionsPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private OpenPositionsPersistenceAdapter adapter;

    @Autowired
    private PositionPersistenceAdapter positions;

    @Test
    void findsTheOpenPositions() {
        Position position =
                positions.save(Position.open("US0378331005", new BigDecimal("10.0000"), new BigDecimal("123.4500")));

        assertThat(adapter.findOpen()).extracting(Position::id).contains(position.id());
    }
}
