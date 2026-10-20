package com.roucoux.cairn.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum SummaryPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    public PeriodBounds bounds(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        return switch (this) {
            case DAY -> new PeriodBounds(today, today);
            case WEEK -> {
                LocalDate monday = yesterday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new PeriodBounds(monday, monday.plusDays(4));
            }
            case MONTH -> {
                LocalDate first = today.minusMonths(1).withDayOfMonth(1);
                yield new PeriodBounds(first, first.with(TemporalAdjusters.lastDayOfMonth()));
            }
            case YEAR -> {
                LocalDate first = LocalDate.of(today.getYear() - 1, 1, 1);
                yield new PeriodBounds(first, LocalDate.of(first.getYear(), 12, 31));
            }
        };
    }
}
