package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record HistoryPoint(LocalDate date, BigDecimal totalEur) {

    public HistoryPoint {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(totalEur, "totalEur");
    }
}
