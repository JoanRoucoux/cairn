package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.AccountNotEmptyException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.port.in.ManageAccountUseCase;
import com.roucoux.cairn.domain.port.out.DeleteAccountPort;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import java.util.List;
import java.util.UUID;

public class AccountService implements ManageAccountUseCase {

    private final LoadAccountsPort loadAccounts;
    private final SaveAccountPort saveAccount;
    private final DeleteAccountPort deleteAccount;
    private final LoadHoldingsPort loadHoldings;
    private final DeleteHoldingPort deleteHolding;
    private final LoadInstrumentsPort loadInstruments;

    public AccountService(
            LoadAccountsPort loadAccounts,
            SaveAccountPort saveAccount,
            DeleteAccountPort deleteAccount,
            LoadHoldingsPort loadHoldings,
            DeleteHoldingPort deleteHolding,
            LoadInstrumentsPort loadInstruments) {
        this.loadAccounts = loadAccounts;
        this.saveAccount = saveAccount;
        this.deleteAccount = deleteAccount;
        this.loadHoldings = loadHoldings;
        this.deleteHolding = deleteHolding;
        this.loadInstruments = loadInstruments;
    }

    @Override
    public Account create(String name, AccountType type, String institution) {
        return saveAccount.save(new Account(UUID.randomUUID(), name, type, institution));
    }

    @Override
    public Account update(UUID id, String name, AccountType type, String institution) {
        loadAccounts.findById(id).orElseThrow(() -> new NotFoundException("account", id));
        return saveAccount.save(new Account(id, name, type, institution));
    }

    @Override
    public void delete(UUID id) {
        loadAccounts.findById(id).orElseThrow(() -> new NotFoundException("account", id));
        List<Holding> holdings = loadHoldings.findByAccount(id);
        List<Holding> eurCash = holdings.stream().filter(this::isEurCash).toList();
        long others = holdings.size() - eurCash.size();
        if (others > 0) {
            throw new AccountNotEmptyException(id, others);
        }
        eurCash.forEach(cash -> deleteHolding.delete(cash.id()));
        deleteAccount.delete(id);
    }

    private boolean isEurCash(Holding holding) {
        return loadInstruments
                .findById(holding.instrumentId())
                .filter(Instrument::isEurCash)
                .isPresent();
    }
}
