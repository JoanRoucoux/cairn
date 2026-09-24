package com.roucoux.cairn.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** The day's portfolio summary sent to Telegram: {@code performance} carries {@link PerformanceRange#D1}. */
public record DailySummary(LocalDate date, Performance performance) {

    public DailySummary {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(performance, "performance");
    }
}
