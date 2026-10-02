package com.roucoux.cairn.domain.port.in;

import com.roucoux.cairn.domain.model.Holding;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ManageHoldingUseCase {

    Holding create(UUID accountId, UUID instrumentId, BigDecimal quantity, BigDecimal averageCost);

    Holding update(UUID id, BigDecimal quantity, BigDecimal averageCost);

    void delete(UUID id);

    Holding buy(UUID id, BigDecimal quantity, BigDecimal unitPrice);

    Optional<Holding> sell(UUID id, BigDecimal quantity);

    Holding changeInstrument(UUID id, UUID instrumentId);
}
