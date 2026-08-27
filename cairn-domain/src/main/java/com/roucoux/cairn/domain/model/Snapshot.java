package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A measured, end-of-day valuation of the whole portfolio, holding by holding, as opposed to the
 * constant-mix reconstruction of {@link HistoryMode#CONSTANT_MIX}.
 */
public record Snapshot(
        LocalDate date, BigDecimal totalEur, Map<UUID, BigDecimal> quantities, Map<UUID, BigDecimal> prices) {

    public Snapshot {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(totalEur, "totalEur");
        Objects.requireNonNull(quantities, "quantities");
        Objects.requireNonNull(prices, "prices");
    }
}
