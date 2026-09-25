package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

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
