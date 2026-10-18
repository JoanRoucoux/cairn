package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.service.HoldingValuationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "spring.datasource.hikari.connection-timeout=250"
        })
@Import({
    HoldingPersistenceAdapter.class,
    AccountPersistenceAdapter.class,
    InstrumentPersistenceAdapter.class,
    QuotePersistenceAdapter.class
})
class ValuationQueryCountIT {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T13:57:00Z"), ZoneOffset.UTC);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    HoldingPersistenceAdapter holdings;

    @Autowired
    AccountPersistenceAdapter accounts;

    @Autowired
    InstrumentPersistenceAdapter instruments;

    @Autowired
    QuotePersistenceAdapter quotes;

    @Autowired
    EntityManager entityManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void valuesTheWholePortfolioInAConstantNumberOfQueries() {
        Account account = accounts.save(new Account(UUID.randomUUID(), "Livret", AccountType.SAVINGS, "Woodgrove"));
        for (int i = 0; i < 26; i++) {
            Instrument instrument = instruments.save(new Instrument(
                    UUID.randomUUID(),
                    "Title " + i,
                    null,
                    "EUR",
                    AssetClass.ETF,
                    PriceSource.YAHOO,
                    "T" + UUID.randomUUID().toString().substring(0, 8),
                    null));
            quotes.upsertAll(List.of(
                    quote(instrument.id(), LocalDate.of(2026, 9, 22), "100"),
                    quote(instrument.id(), LocalDate.of(2026, 9, 23), "101")));
            holdings.save(
                    new Holding(UUID.randomUUID(), account.id(), instrument.id(), BigDecimal.ONE, null, Instant.EPOCH));
        }
        entityManager.flush();
        entityManager.clear();
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<ValuedHolding> valued =
                new HoldingValuationService(instruments, accounts, quotes, CLOCK).valueAll(holdings.findAll());

        assertThat(valued).hasSize(26);
        assertThat(valued).allSatisfy(line -> assertThat(line.previousQuote()).isPresent());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    private static Quote quote(UUID instrumentId, LocalDate asOf, String price) {
        return new Quote(instrumentId, asOf, new BigDecimal(price), "EUR", PriceSource.YAHOO, Instant.EPOCH);
    }
}
