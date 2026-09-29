package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.AccountNotEmptyException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ManageAccountUseCase;
import com.roucoux.cairn.domain.port.out.DeleteAccountPort;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountDomainConfigTest {

    private static final Account ACCOUNT = new Account(UUID.randomUUID(), "Fortuneo", AccountType.SAVINGS, "Fortuneo");
    private static final Instrument EUROS =
            new Instrument(UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);

    @Test
    void wiresTheAccountServiceThroughSoDeletingAnAccountRemovesItsEurCashHolding() {
        List<Account> accounts = new ArrayList<>(List.of(ACCOUNT));
        List<UUID> deletedAccountIds = new ArrayList<>();
        Holding cash = new Holding(UUID.randomUUID(), ACCOUNT.id(), EUROS.id(), new java.math.BigDecimal("1500"), null);
        List<Holding> holdings = new ArrayList<>(List.of(cash));
        List<UUID> deletedHoldingIds = new ArrayList<>();

        LoadAccountsPort loadAccounts = new LoadAccountsPort() {
            @Override
            public List<Account> findAll() {
                return List.copyOf(accounts);
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return accounts.stream().filter(a -> a.id().equals(id)).findFirst();
            }
        };
        SaveAccountPort saveAccount = account -> {
            accounts.add(account);
            return account;
        };
        DeleteAccountPort deleteAccount = id -> {
            accounts.removeIf(a -> a.id().equals(id));
            deletedAccountIds.add(id);
        };
        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
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
                return Optional.empty();
            }

            @Override
            public List<Holding> findByInstrument(UUID instrumentId) {
                return List.of();
            }

            @Override
            public List<Holding> findByAccount(UUID accountId) {
                return holdings.stream()
                        .filter(h -> h.accountId().equals(accountId))
                        .toList();
            }
        };
        DeleteHoldingPort deleteHolding = id -> {
            holdings.removeIf(h -> h.id().equals(id));
            deletedHoldingIds.add(id);
        };
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of(EUROS);
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return EUROS.id().equals(id) ? Optional.of(EUROS) : Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };

        ManageAccountUseCase useCase = new AccountDomainConfig()
                .manageAccountUseCase(
                        loadAccounts, saveAccount, deleteAccount, loadHoldings, deleteHolding, loadInstruments);
        useCase.delete(ACCOUNT.id());

        assertThat(deletedAccountIds).containsExactly(ACCOUNT.id());
        assertThat(deletedHoldingIds).containsExactly(cash.id());
    }

    @Test
    void wiresTheAccountServiceThroughSoARemainingLineRefusesTheDeletion() {
        List<Account> accounts = new ArrayList<>(List.of(ACCOUNT));
        Holding line =
                new Holding(UUID.randomUUID(), ACCOUNT.id(), UUID.randomUUID(), new java.math.BigDecimal("4"), null);
        List<Holding> holdings = new ArrayList<>(List.of(line));

        LoadAccountsPort loadAccounts = new LoadAccountsPort() {
            @Override
            public List<Account> findAll() {
                return List.copyOf(accounts);
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return accounts.stream().filter(a -> a.id().equals(id)).findFirst();
            }
        };
        SaveAccountPort saveAccount = account -> account;
        DeleteAccountPort deleteAccount = id -> accounts.removeIf(a -> a.id().equals(id));
        LoadHoldingsPort loadHoldings = new LoadHoldingsPort() {
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
                return Optional.empty();
            }

            @Override
            public List<Holding> findByInstrument(UUID instrumentId) {
                return List.of();
            }

            @Override
            public List<Holding> findByAccount(UUID accountId) {
                return holdings.stream()
                        .filter(h -> h.accountId().equals(accountId))
                        .toList();
            }
        };
        DeleteHoldingPort deleteHolding = id -> holdings.removeIf(h -> h.id().equals(id));
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of();
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };

        ManageAccountUseCase useCase = new AccountDomainConfig()
                .manageAccountUseCase(
                        loadAccounts, saveAccount, deleteAccount, loadHoldings, deleteHolding, loadInstruments);

        assertThatThrownBy(() -> useCase.delete(ACCOUNT.id())).isInstanceOf(AccountNotEmptyException.class);
    }
}
