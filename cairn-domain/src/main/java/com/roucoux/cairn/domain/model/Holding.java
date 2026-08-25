package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Holding(UUID id, UUID accountId, UUID instrumentId, BigDecimal quantity, BigDecimal averageCost) {

    public Holding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(quantity, "quantity");
    }

    public Optional<BigDecimal> costBasis() {
        return Optional.ofNullable(averageCost);
    }
}
