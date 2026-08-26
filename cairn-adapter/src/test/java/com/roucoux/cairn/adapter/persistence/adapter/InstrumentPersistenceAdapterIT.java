package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import java.util.List;
import java.util.Set;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(InstrumentPersistenceAdapter.class)
class InstrumentPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    InstrumentPersistenceAdapter instruments;

    @Test
    void savesAndReadsBackAnInstrument() {
        Instrument saved = instruments.save(etf("CW8.PA"));

        assertThat(instruments.findById(saved.id()))
                .hasValueSatisfying(found -> assertThat(found.sourceRef()).isEqualTo("CW8.PA"));
    }

    @Test
    void findsOnlyRefreshableInstrumentsOfTheRequestedAssetClasses() {
        instruments.save(etf("CW8.PA"));
        instruments.save(cashInstrument());

        List<Instrument> refreshable = instruments.findRefreshable(Set.of(AssetClass.ETF, AssetClass.CASH));

        assertThat(refreshable).extracting(Instrument::sourceRef).containsExactly("CW8.PA");
    }

    private Instrument etf(String sourceRef) {
        return new Instrument(
                UUID.randomUUID(), "MSCI World", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, sourceRef, null);
    }

    private Instrument cashInstrument() {
        return new Instrument(
                UUID.randomUUID(), "Cash EUR", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);
    }
}
