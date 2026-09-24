package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * A measured, end-of-day valuation of the whole portfolio, as opposed to the constant-mix
 * reconstruction of {@link HistoryMode#CONSTANT_MIX}. Ventilated by {@link AccountType} and
 * {@link AssetClass} name, each dimension's values summing to {@code totalEur}.
 */
public record Snapshot(
        LocalDate date,
        BigDecimal totalEur,
        Map<String, BigDecimal> byAccountType,
        Map<String, BigDecimal> byAssetClass) {

    public Snapshot {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(totalEur, "totalEur");
        Objects.requireNonNull(byAccountType, "byAccountType");
        Objects.requireNonNull(byAssetClass, "byAssetClass");
    }
}
