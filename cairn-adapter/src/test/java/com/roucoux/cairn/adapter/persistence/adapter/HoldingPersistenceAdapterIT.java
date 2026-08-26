package com.roucoux.cairn.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({HoldingPersistenceAdapter.class, AccountPersistenceAdapter.class, InstrumentPersistenceAdapter.class})
class HoldingPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    HoldingPersistenceAdapter holdings;

    @Autowired
    AccountPersistenceAdapter accounts;

    @Autowired
    InstrumentPersistenceAdapter instruments;

    @Test
    void savesAndReadsBackAHolding() {
        Account account = accounts.save(new Account(UUID.randomUUID(), "Binance", AccountType.CRYPTO, "Binance"));
        Instrument bitcoin = instruments.save(new Instrument(
                UUID.randomUUID(), "Bitcoin", null, "EUR", AssetClass.CRYPTO, PriceSource.COINGECKO, "bitcoin", null));

        Holding saved = holdings.save(
                new Holding(UUID.randomUUID(), account.id(), bitcoin.id(), new BigDecimal("0.00005752"), null));

        assertThat(holdings.findById(saved.id()).orElseThrow().quantity()).isEqualByComparingTo("0.00005752");
    }

    @Test
    void keepsTwelveDecimalsOfQuantity() {
        // The starter's numeric(19,4) scale would round this quantity to 0.0001,
        // i.e. 6.23 EUR instead of 3.58 EUR at the 2026-08-21 quote.
        Holding saved = givenAHoldingOf(new BigDecimal("0.000057520000"));

        assertThat(holdings.findById(saved.id()).orElseThrow().quantity()).isEqualByComparingTo("0.00005752");
    }

    @Test
    void findsAHoldingByAccountAndInstrument() {
        Holding saved = givenAHoldingOf(new BigDecimal("4"));

        assertThat(holdings.findByAccountAndInstrument(saved.accountId(), saved.instrumentId()))
                .isPresent();
    }

    @Test
    void deletesAHolding() {
        Holding saved = givenAHoldingOf(new BigDecimal("4"));

        holdings.delete(saved.id());

        assertThat(holdings.findById(saved.id())).isEmpty();
    }

    private Holding givenAHoldingOf(BigDecimal quantity) {
        Account account = accounts.save(new Account(UUID.randomUUID(), "Binance", AccountType.CRYPTO, "Binance"));
        Instrument bitcoin = instruments.save(new Instrument(
                UUID.randomUUID(), "Bitcoin", null, "EUR", AssetClass.CRYPTO, PriceSource.COINGECKO, "bitcoin", null));
        return holdings.save(new Holding(UUID.randomUUID(), account.id(), bitcoin.id(), quantity, null));
    }
}
