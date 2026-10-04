package com.roucoux.cairn.infrastructure.transaction;

import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.NewInstrument;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HoldingTransaction {

    private final ManageHoldingUseCase manageHolding;

    HoldingTransaction(ManageHoldingUseCase manageHolding) {
        this.manageHolding = manageHolding;
    }

    @Transactional
    public Holding create(UUID accountId, UUID instrumentId, BigDecimal quantity, BigDecimal averageCost) {
        return manageHolding.create(accountId, instrumentId, quantity, averageCost);
    }

    @Transactional
    public Holding createWithNewInstrument(
            UUID accountId, NewInstrument instrument, BigDecimal quantity, BigDecimal averageCost) {
        return manageHolding.createWithNewInstrument(accountId, instrument, quantity, averageCost);
    }

    @Transactional
    public void delete(UUID id) {
        manageHolding.delete(id);
    }

    @Transactional
    public Optional<Holding> sell(UUID id, BigDecimal quantity) {
        return manageHolding.sell(id, quantity);
    }
}
