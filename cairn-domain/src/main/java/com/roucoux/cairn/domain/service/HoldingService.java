package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.CashHoldingTradeException;
import com.roucoux.cairn.domain.exception.business.DuplicateHoldingException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.business.ZeroQuantityException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public class HoldingService implements ManageHoldingUseCase {

    private final LoadHoldingsPort loadHoldings;
    private final SaveHoldingPort saveHolding;
    private final DeleteHoldingPort deleteHolding;
    private final LoadAccountsPort loadAccounts;
    private final LoadInstrumentsPort loadInstruments;

    public HoldingService(
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding,
            DeleteHoldingPort deleteHolding,
            LoadAccountsPort loadAccounts,
            LoadInstrumentsPort loadInstruments) {
        this.loadHoldings = loadHoldings;
        this.saveHolding = saveHolding;
        this.deleteHolding = deleteHolding;
        this.loadAccounts = loadAccounts;
        this.loadInstruments = loadInstruments;
    }

    @Override
    public Holding create(UUID accountId, UUID instrumentId, BigDecimal quantity, BigDecimal averageCost) {
        requireNonZero(quantity);
        loadAccounts.findById(accountId).orElseThrow(() -> new NotFoundException("account", accountId));
        loadInstruments.findById(instrumentId).orElseThrow(() -> new NotFoundException("instrument", instrumentId));
        loadHoldings.findByAccountAndInstrument(accountId, instrumentId).ifPresent(existing -> {
            throw new DuplicateHoldingException(accountId, instrumentId);
        });
        return saveHolding.save(new Holding(UUID.randomUUID(), accountId, instrumentId, quantity, averageCost));
    }

    @Override
    public Holding update(UUID id, BigDecimal quantity, BigDecimal averageCost) {
        requireNonZero(quantity);
        Holding existing = loadHoldings.findById(id).orElseThrow(() -> new NotFoundException("holding", id));
        return saveHolding.save(
                new Holding(existing.id(), existing.accountId(), existing.instrumentId(), quantity, averageCost));
    }

    @Override
    public void delete(UUID id) {
        loadHoldings.findById(id).orElseThrow(() -> new NotFoundException("holding", id));
        deleteHolding.delete(id);
    }

    @Override
    public Holding buy(UUID id, BigDecimal quantity, BigDecimal unitPrice) {
        Holding existing = tradable(id);
        return saveHolding.save(existing.buy(quantity, unitPrice));
    }

    @Override
    public Optional<Holding> sell(UUID id, BigDecimal quantity) {
        Holding existing = tradable(id);
        Optional<Holding> remaining = existing.sell(quantity);
        if (remaining.isEmpty()) {
            deleteHolding.delete(id);
            return Optional.empty();
        }
        return Optional.of(saveHolding.save(remaining.get()));
    }

    private Holding tradable(UUID id) {
        Holding existing = loadHoldings.findById(id).orElseThrow(() -> new NotFoundException("holding", id));
        loadInstruments
                .findById(existing.instrumentId())
                .filter(instrument -> instrument.assetClass() == AssetClass.CASH)
                .ifPresent(cash -> {
                    throw new CashHoldingTradeException(id);
                });
        return existing;
    }

    private static void requireNonZero(BigDecimal quantity) {
        if (quantity == null || quantity.signum() == 0) {
            throw new ZeroQuantityException();
        }
    }
}
