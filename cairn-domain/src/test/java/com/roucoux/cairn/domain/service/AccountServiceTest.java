package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.AccountNotEmptyException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.business.SavingsAccountLineException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.DeleteAccountPort;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountServiceTest {

    @Test
    void createsAnAccount() {
        Fixture fixture = new Fixture();

        Account created = fixture.service().create("Northwind PEA", AccountType.PEA, "Northwind Bank");

        assertThat(fixture.accounts).containsKey(created.id());
    }

    @Test
    void updatesNameTypeAndInstitution() {
        Fixture fixture = new Fixture();
        UUID id = fixture.account("Contoso Trading", AccountType.CTO);

        Account updated = fixture.service().update(id, "Northwind PEA", AccountType.PEA, "Northwind Bank");

        assertThat(updated).isEqualTo(new Account(id, "Northwind PEA", AccountType.PEA, "Northwind Bank"));
    }

    @Test
    void refusesToTurnAnAccountHoldingSecuritiesIntoASavingsAccount() {
        Fixture fixture = new Fixture();
        UUID id = fixture.account("Contoso Trading", AccountType.CTO);
        fixture.holding(id, fixture.eurCash(), "100");
        fixture.holding(id, fixture.livretA(), "200");

        assertThatThrownBy(() ->
                        fixture.service().update(id, "Contoso Trading", AccountType.SAVINGS, "Contoso Securities"))
                .isInstanceOf(SavingsAccountLineException.class);
        assertThat(fixture.accounts.get(id).type()).isEqualTo(AccountType.CTO);
    }

    @Test
    void turnsAnAccountHoldingOnlyEuroCashIntoASavingsAccount() {
        Fixture fixture = new Fixture();
        UUID id = fixture.account("Contoso Trading", AccountType.CTO);
        fixture.holding(id, fixture.eurCash(), "100");

        Account updated = fixture.service().update(id, "Contoso Trading", AccountType.SAVINGS, "Contoso Securities");

        assertThat(updated.type()).isEqualTo(AccountType.SAVINGS);
    }

    @Test
    void updatingAnUnknownAccountIsNotFound() {
        assertThatThrownBy(() -> new Fixture().service().update(UUID.randomUUID(), "x", AccountType.PEA, "y"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletesAnAccountThatHoldsOnlyEuroCash() {
        Fixture fixture = new Fixture();
        UUID id = fixture.account("Livret A", AccountType.SAVINGS);
        fixture.holding(id, fixture.eurCash(), "1500");

        fixture.service().delete(id);

        assertThat(fixture.accounts).doesNotContainKey(id);
        assertThat(fixture.holdings).isEmpty();
        assertThat(fixture.deletedInstruments).isEmpty();
        assertThat(fixture.instruments).hasSize(1);
    }

    @Test
    void refusesToDeleteAnAccountThatStillHoldsLines() {
        Fixture fixture = new Fixture();
        UUID id = fixture.account("Livret A", AccountType.SAVINGS);
        fixture.holding(id, fixture.eurCash(), "1500");
        fixture.holding(id, fixture.livretA(), "20000");

        assertThatThrownBy(() -> fixture.service().delete(id))
                .isInstanceOf(AccountNotEmptyException.class)
                .hasMessageContaining("1");
        assertThat(fixture.accounts).containsKey(id);
        assertThat(fixture.holdings).hasSize(2);
    }

    @Test
    void deletingAnUnknownAccountIsNotFound() {
        assertThatThrownBy(() -> new Fixture().service().delete(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    private static final class Fixture {

        private final Map<UUID, Account> accounts = new HashMap<>();
        private final List<Holding> holdings = new ArrayList<>();
        private final List<UUID> deletedInstruments = new ArrayList<>();
        private final Map<UUID, Instrument> instruments = new HashMap<>();

        UUID account(String name, AccountType type) {
            UUID id = UUID.randomUUID();
            accounts.put(id, new Account(id, name, type, name));
            return id;
        }

        UUID eurCash() {
            UUID id = UUID.randomUUID();
            instruments.put(
                    id, new Instrument(id, "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null));
            return id;
        }

        UUID livretA() {
            UUID id = UUID.randomUUID();
            instruments.put(
                    id, new Instrument(id, "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null));
            return id;
        }

        void holding(UUID accountId, UUID instrumentId, String quantity) {
            holdings.add(new Holding(
                    UUID.randomUUID(),
                    accountId,
                    instrumentId,
                    new BigDecimal(quantity),
                    BigDecimal.ONE,
                    Instant.EPOCH));
        }

        AccountService service() {
            return new AccountService(
                    new InMemoryLoadAccountsPort(),
                    new InMemorySaveAccountPort(),
                    new InMemoryDeleteAccountPort(),
                    new InMemoryLoadHoldingsPort(),
                    new InMemoryDeleteHoldingPort(),
                    new InMemoryLoadInstrumentsPort(),
                    deletedInstruments::add);
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

        private final class InMemorySaveAccountPort implements SaveAccountPort {
            @Override
            public Account save(Account account) {
                accounts.put(account.id(), account);
                return account;
            }
        }

        private final class InMemoryDeleteAccountPort implements DeleteAccountPort {
            @Override
            public void delete(UUID id) {
                accounts.remove(id);
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

            @Override
            public List<Holding> findByAccount(UUID accountId) {
                return holdings.stream()
                        .filter(h -> h.accountId().equals(accountId))
                        .toList();
            }
        }

        private final class InMemoryDeleteHoldingPort implements DeleteHoldingPort {
            @Override
            public void delete(UUID id) {
                holdings.removeIf(h -> h.id().equals(id));
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
