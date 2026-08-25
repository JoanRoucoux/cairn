package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.DuplicateHoldingException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldingServiceTest {

    @Test
    void createsAHolding() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        Holding created = fixture.service()
                .create(fixture.accountId(), fixture.instrumentId(), new BigDecimal("4"), new BigDecimal("43.64"));

        assertThat(created.quantity()).isEqualByComparingTo("4");
        assertThat(created.averageCost()).isEqualByComparingTo("43.64");
    }

    @Test
    void acceptsAHoldingWithoutACostBasis() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        Holding created =
                fixture.service().create(fixture.accountId(), fixture.instrumentId(), new BigDecimal("296"), null);

        assertThat(created.averageCost()).isNull();
    }

    @Test
    void rejectsASecondHoldingOfTheSameInstrumentInTheSameAccount() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() ->
                        fixture.service().create(fixture.accountId(), fixture.instrumentId(), BigDecimal.ONE, null))
                .isInstanceOf(DuplicateHoldingException.class);
    }

    @Test
    void rejectsAZeroQuantity() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        assertThatThrownBy(() ->
                        fixture.service().create(fixture.accountId(), fixture.instrumentId(), BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsAnUnknownAccount() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        assertThatThrownBy(
                        () -> fixture.service().create(UUID.randomUUID(), fixture.instrumentId(), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsAnUnknownInstrument() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        assertThatThrownBy(() -> fixture.service().create(fixture.accountId(), UUID.randomUUID(), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesQuantityAndCostBasis() {
        Fixture fixture = Fixture.withExistingHolding();

        Holding updated = fixture.service().update(fixture.holdingId(), new BigDecimal("31"), new BigDecimal("394.25"));

        assertThat(updated.quantity()).isEqualByComparingTo("31");
    }

    @Test
    void rejectsUpdatingAnUnknownHolding() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() -> fixture.service().update(UUID.randomUUID(), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUpdatingWithAZeroQuantity() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() -> fixture.service().update(fixture.holdingId(), BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void deletesAHolding() {
        Fixture fixture = Fixture.withExistingHolding();

        fixture.service().delete(fixture.holdingId());

        assertThat(fixture.deleted()).containsExactly(fixture.holdingId());
    }

    @Test
    void rejectsDeletingAnUnknownHolding() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() -> fixture.service().delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    private static final class Fixture {

        private final List<Holding> holdings = new ArrayList<>();
        private final List<UUID> deletedIds = new ArrayList<>();
        private final Map<UUID, Account> accounts;
        private final Map<UUID, Instrument> instruments;
        private final UUID accountId;
        private final UUID instrumentId;
        private final UUID holdingId;

        private Fixture(UUID accountId, UUID instrumentId, UUID holdingId) {
            this.accountId = accountId;
            this.instrumentId = instrumentId;
            this.holdingId = holdingId;
            this.accounts = Map.of(accountId, new Account(accountId, "Livret A", AccountType.SAVINGS, "Bank"));
            this.instruments = Map.of(
                    instrumentId,
                    new Instrument(
                            instrumentId,
                            "CW8",
                            "FR0011871128",
                            "EUR",
                            AssetClass.ETF,
                            PriceSource.MANUAL,
                            null,
                            null));
        }

        static Fixture withKnownAccountAndInstrument() {
            return new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        }

        static Fixture withExistingHolding() {
            Fixture fixture = withKnownAccountAndInstrument();
            fixture.holdings.add(
                    new Holding(fixture.holdingId, fixture.accountId, fixture.instrumentId, BigDecimal.ONE, null));
            return fixture;
        }

        UUID accountId() {
            return accountId;
        }

        UUID instrumentId() {
            return instrumentId;
        }

        UUID holdingId() {
            return holdingId;
        }

        List<UUID> deleted() {
            return deletedIds;
        }

        HoldingService service() {
            return new HoldingService(
                    new InMemoryLoadHoldingsPort(),
                    new InMemorySaveHoldingPort(),
                    new InMemoryDeleteHoldingPort(),
                    new InMemoryLoadAccountsPort(),
                    new InMemoryLoadInstrumentsPort());
        }

        private final class InMemoryLoadHoldingsPort implements LoadHoldingsPort {
            @Override
            public List<Holding> findAll() {
                return List.copyOf(holdings);
            }

            @Override
            public Optional<Holding> findById(UUID id) {
                return holdings.stream().filter(h -> h.id().equals(id)).findFirst();
            }

            @Override
            public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
                return holdings.stream()
                        .filter(h -> h.accountId().equals(accountId)
                                && h.instrumentId().equals(instrumentId))
                        .findFirst();
            }
        }

        private final class InMemorySaveHoldingPort implements SaveHoldingPort {
            @Override
            public Holding save(Holding holding) {
                holdings.removeIf(h -> h.id().equals(holding.id()));
                holdings.add(holding);
                return holding;
            }
        }

        private final class InMemoryDeleteHoldingPort implements DeleteHoldingPort {
            @Override
            public void delete(UUID id) {
                holdings.removeIf(h -> h.id().equals(id));
                deletedIds.add(id);
            }
        }

        private final class InMemoryLoadAccountsPort implements LoadAccountsPort {
            @Override
            public List<Account> findAll() {
                return List.copyOf(accounts.values());
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return Optional.ofNullable(accounts.get(id));
            }
        }

        private final class InMemoryLoadInstrumentsPort implements LoadInstrumentsPort {
            @Override
            public List<Instrument> findAll() {
                return List.copyOf(instruments.values());
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return Optional.ofNullable(instruments.get(id));
            }

            @Override
            public List<Instrument> findRefreshable(java.util.Set<AssetClass> assetClasses) {
                return List.of();
            }
        }
    }
}
