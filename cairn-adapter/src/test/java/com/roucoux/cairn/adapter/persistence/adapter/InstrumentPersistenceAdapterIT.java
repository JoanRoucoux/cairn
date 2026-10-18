package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
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
@Import(InstrumentPersistenceAdapter.class)
class InstrumentPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    InstrumentPersistenceAdapter instruments;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void savesAndReadsBackAnInstrument() {
        Instrument saved = instruments.save(etf("ETF.PA"));

        assertThat(instruments.findById(saved.id()))
                .hasValueSatisfying(found -> assertThat(found.sourceRef()).isEqualTo("ETF.PA"));
    }

    @Test
    void roundTripsTheSymbol() {
        Instrument saved = instruments.save(new Instrument(
                UUID.randomUUID(),
                "Ethereum",
                null,
                "ETH",
                "EUR",
                AssetClass.CRYPTO,
                PriceSource.COINGECKO,
                "ethereum",
                null));

        assertThat(instruments.findById(saved.id()))
                .hasValueSatisfying(found -> assertThat(found.symbol()).isEqualTo("ETH"));
    }

    @Test
    void findsOnlyRefreshableInstrumentsOfTheRequestedAssetClasses() {
        instruments.save(etf("ETF.PA"));
        instruments.save(cashInstrument());

        List<Instrument> refreshable = instruments.findRefreshable(Set.of(AssetClass.ETF, AssetClass.CASH));

        assertThat(refreshable).extracting(Instrument::sourceRef).containsExactly("ETF.PA");
    }

    @Test
    void anUpdateKeepsTheOriginalCreationInstant() {
        Instrument saved = instruments.save(etf("ETF.PA"));
        entityManager.flush();
        Timestamp original =
                Timestamp.from(Instant.now().minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MILLIS));
        jdbc.update("update instruments set created_at = ? where id = ?", original, saved.id());

        instruments.save(new Instrument(
                saved.id(), "MSCI World renamed", null, "EUR", AssetClass.ETF, PriceSource.YAHOO, "ETF.PA", null));
        entityManager.flush();

        assertThat(jdbc.queryForObject("select created_at from instruments where id = ?", Timestamp.class, saved.id()))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject("select name from instruments where id = ?", String.class, saved.id()))
                .isEqualTo("MSCI World renamed");
    }

    @Test
    void deletesAnInstrument() {
        Instrument saved = instruments.save(etf("ETF.PA"));

        instruments.delete(saved.id());

        assertThat(instruments.findById(saved.id())).isEmpty();
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
