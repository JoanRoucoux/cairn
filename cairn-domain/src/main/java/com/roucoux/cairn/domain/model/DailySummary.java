package com.roucoux.cairn.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record DailySummary(LocalDate date, Performance performance) {

    public DailySummary {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(performance, "performance");
    }
}
