package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.NegativeCashBalanceException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.SetCashBalanceUseCase;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class CashBalanceService implements SetCashBalanceUseCase {

    private final LoadAccountsPort loadAccounts;
    private final LoadInstrumentsPort loadInstruments;
    private final SaveInstrumentPort saveInstrument;
    private final LoadHoldingsPort loadHoldings;
    private final SaveHoldingPort saveHolding;
    private final DeleteHoldingPort deleteHolding;
    private final Clock clock;

    public CashBalanceService(
            LoadAccountsPort loadAccounts,
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding,
            DeleteHoldingPort deleteHolding,
            Clock clock) {
        this.loadAccounts = loadAccounts;
        this.loadInstruments = loadInstruments;
        this.saveInstrument = saveInstrument;
        this.loadHoldings = loadHoldings;
        this.saveHolding = saveHolding;
        this.deleteHolding = deleteHolding;
        this.clock = clock;
    }

    @Override
    public void setCashBalance(UUID accountId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new NegativeCashBalanceException();
        }
        Account account =
                loadAccounts.findById(accountId).orElseThrow(() -> new NotFoundException("account", accountId));

        Optional<UUID> eurosId = findEurCash();

        if (amount.signum() == 0 && account.type() != AccountType.SAVINGS) {
            eurosId.flatMap(id -> loadHoldings.findByAccountAndInstrument(accountId, id))
                    .ifPresent(holding -> deleteHolding.delete(holding.id()));
            return;
        }

        UUID instrumentId = eurosId.orElseGet(this::createEurCash);
        Instant now = clock.instant();
        Holding holding = loadHoldings
                .findByAccountAndInstrument(accountId, instrumentId)
                .map(current -> new Holding(current.id(), accountId, instrumentId, amount, BigDecimal.ONE, now))
                .orElseGet(() -> new Holding(UUID.randomUUID(), accountId, instrumentId, amount, BigDecimal.ONE, now));
        saveHolding.save(holding);
    }

    private Optional<UUID> findEurCash() {
        return loadInstruments.findAll().stream()
                .filter(Instrument::isEurCash)
                .findFirst()
                .map(Instrument::id);
    }

    private UUID createEurCash() {
        return saveInstrument
                .save(new Instrument(
                        UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null))
                .id();
    }
}
