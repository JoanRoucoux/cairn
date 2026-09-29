package com.roucoux.cairn.domain.model;

import com.roucoux.cairn.domain.exception.business.InsufficientQuantityException;
import com.roucoux.cairn.domain.exception.business.InvalidTradeException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Holding(UUID id, UUID accountId, UUID instrumentId, BigDecimal quantity, BigDecimal averageCost) {

    private static final int AVERAGE_COST_SCALE = 6;

    public Holding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(quantity, "quantity");
    }

    public Optional<BigDecimal> costBasis() {
        return Optional.ofNullable(averageCost);
    }

    public Holding buy(BigDecimal boughtQuantity, BigDecimal unitPrice) {
        requirePositive(boughtQuantity, "quantity");
        requirePositive(unitPrice, "unit price");
        BigDecimal newQuantity = quantity.add(boughtQuantity);
        BigDecimal newAverageCost = averageCost == null
                ? unitPrice.setScale(AVERAGE_COST_SCALE, RoundingMode.HALF_UP)
                : quantity.multiply(averageCost)
                        .add(boughtQuantity.multiply(unitPrice))
                        .divide(newQuantity, AVERAGE_COST_SCALE, RoundingMode.HALF_UP);
        return new Holding(id, accountId, instrumentId, newQuantity, newAverageCost);
    }

    public Optional<Holding> sell(BigDecimal soldQuantity) {
        requirePositive(soldQuantity, "quantity");
        int comparison = soldQuantity.compareTo(quantity);
        if (comparison > 0) {
            throw new InsufficientQuantityException(quantity, soldQuantity);
        }
        if (comparison == 0) {
            return Optional.empty();
        }
        return Optional.of(new Holding(id, accountId, instrumentId, quantity.subtract(soldQuantity), averageCost));
    }

    private static void requirePositive(BigDecimal value, String what) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidTradeException(what + " must be positive");
        }
    }
}
