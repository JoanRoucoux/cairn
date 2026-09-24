package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.NegativeCashBalanceException;
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
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashBalanceServiceTest {

    @Test
    void rejectsAnUnknownAccount() {
        Fixture fixture = Fixture.withKnownAccount();

        assertThatThrownBy(() -> fixture.service().setCashBalance(UUID.randomUUID(), BigDecimal.TEN))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createsTheEurosInstrumentAndTheHoldingWhenNeitherExists() {
        Fixture fixture = Fixture.withKnownAccount();

        fixture.service().setCashBalance(fixture.accountId(), new BigDecimal("500"));

        assertThat(fixture.instruments()).hasSize(1);
        Instrument euros = fixture.instruments().get(0);
        assertThat(euros.name()).isEqualTo("Euros");
        assertThat(euros.currency()).isEqualTo("EUR");
        assertThat(euros.assetClass()).isEqualTo(AssetClass.CASH);
        assertThat(euros.priceSource()).isEqualTo(PriceSource.MANUAL);
        assertThat(euros.sourceRef()).isEqualTo("EUR");
        assertThat(fixture.holdings()).hasSize(1);
        Holding holding = fixture.holdings().get(0);
        assertThat(holding.accountId()).isEqualTo(fixture.accountId());
        assertThat(holding.instrumentId()).isEqualTo(euros.id());
        assertThat(holding.quantity()).isEqualByComparingTo("500");
        assertThat(holding.averageCost()).isEqualByComparingTo("1");
    }

    @Test
    void reusesTheExistingEurosInstrumentWithoutCreatingAnotherOne() {
        Fixture fixture = Fixture.withEurosInstrument();

        fixture.service().setCashBalance(fixture.accountId(), new BigDecimal("800"));

        assertThat(fixture.instruments()).hasSize(1);
        Holding holding = fixture.holdings().get(0);
        assertThat(holding.instrumentId()).isEqualTo(fixture.eurosId());
        assertThat(holding.quantity()).isEqualByComparingTo("800");
        assertThat(holding.averageCost()).isEqualByComparingTo("1");
    }

    @Test
    void replacesTheQuantityOfAnExistingHoldingKeepingItsId() {
        Fixture fixture = Fixture.withExistingHolding(new BigDecimal("500"));

        fixture.service().setCashBalance(fixture.accountId(), new BigDecimal("800"));

        assertThat(fixture.instruments()).hasSize(1);
        assertThat(fixture.holdings()).hasSize(1);
        Holding holding = fixture.holdings().get(0);
        assertThat(holding.id()).isEqualTo(fixture.holdingId());
        assertThat(holding.quantity()).isEqualByComparingTo("800");
        assertThat(holding.averageCost()).isEqualByComparingTo("1");
    }

    @Test
    void zeroWithAnExistingHoldingDeletesIt() {
        Fixture fixture = Fixture.withExistingHolding(new BigDecimal("500"));

        fixture.service().setCashBalance(fixture.accountId(), BigDecimal.ZERO);

        assertThat(fixture.holdings()).isEmpty();
        assertThat(fixture.deleted()).containsExactly(fixture.holdingId());
    }

    @Test
    void zeroWithoutAnExistingHoldingWritesNothing() {
        Fixture fixture = Fixture.withKnownAccount();

        fixture.service().setCashBalance(fixture.accountId(), BigDecimal.ZERO);

        assertThat(fixture.instruments()).isEmpty();
        assertThat(fixture.holdings()).isEmpty();
    }

    @Test
    void rejectsANegativeAmountWithoutWritingAnything() {
        Fixture fixture = Fixture.withKnownAccount();

        assertThatThrownBy(() -> fixture.service().setCashBalance(fixture.accountId(), new BigDecimal("-1")))
                .isInstanceOf(NegativeCashBalanceException.class);
        assertThat(fixture.instruments()).isEmpty();
        assertThat(fixture.holdings()).isEmpty();
    }

    private static final class Fixture {

        private final List<Instrument> instruments = new ArrayList<>();
        private final List<Holding> holdings = new ArrayList<>();
        private final List<UUID> deletedIds = new ArrayList<>();
        private final Map<UUID, Account> accounts;
        private final UUID accountId;
        private UUID holdingId;

        private Fixture(UUID accountId) {
            this.accountId = accountId;
            this.accounts = Map.of(accountId, new Account(accountId, "Fortuneo", AccountType.SAVINGS, "Fortuneo"));
        }

        static Fixture withKnownAccount() {
            return new Fixture(UUID.randomUUID());
        }

        static Fixture withEurosInstrument() {
            Fixture fixture = withKnownAccount();
            fixture.instruments.add(new Instrument(
                    UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null));
            return fixture;
        }

        static Fixture withExistingHolding(BigDecimal quantity) {
            Fixture fixture = withEurosInstrument();
            fixture.holdingId = UUID.randomUUID();
            fixture.holdings.add(
                    new Holding(fixture.holdingId, fixture.accountId, fixture.eurosId(), quantity, BigDecimal.ONE));
            return fixture;
        }

        UUID accountId() {
            return accountId;
        }

        UUID eurosId() {
            return instruments.get(0).id();
        }

        UUID holdingId() {
            return holdingId;
        }

        List<Instrument> instruments() {
            return List.copyOf(instruments);
        }

        List<Holding> holdings() {
            return List.copyOf(holdings);
        }

        List<UUID> deleted() {
            return deletedIds;
        }

        CashBalanceService service() {
            return new CashBalanceService(
                    new InMemoryLoadAccountsPort(),
                    new InMemoryLoadInstrumentsPort(),
                    new InMemorySaveInstrumentPort(),
                    new InMemoryLoadHoldingsPort(),
                    new InMemorySaveHoldingPort(),
                    new InMemoryDeleteHoldingPort());
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
                return List.copyOf(instruments);
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return instruments.stream().filter(i -> i.id().equals(id)).findFirst();
            }

            @Override
            public List<Instrument> findRefreshable(java.util.Set<AssetClass> assetClasses) {
                return List.of();
            }
        }

        private final class InMemorySaveInstrumentPort implements SaveInstrumentPort {
            @Override
            public Instrument save(Instrument instrument) {
                instruments.removeIf(i -> i.id().equals(instrument.id()));
                instruments.add(instrument);
                return instrument;
            }
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

            @Override
            public List<Holding> findByInstrument(UUID instrumentId) {
                return holdings.stream()
                        .filter(h -> h.instrumentId().equals(instrumentId))
                        .toList();
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
    }
}
