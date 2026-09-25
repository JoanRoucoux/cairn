package com.roucoux.cairn.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** Must match cairn-web's rangeStart in chart-range.ts. */
public enum PerformanceRange {
    D1(1),
    D7(7),
    M1(31),
    Y1(366),
    Y5(1827),
    MAX(null);

    private final Integer daysBack;

    PerformanceRange(Integer daysBack) {
        this.daysBack = daysBack;
    }

    public LocalDate to(Clock clock, ZoneId zone) {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    public Optional<LocalDate> from(Clock clock, ZoneId zone) {
        return Optional.ofNullable(daysBack).map(days -> to(clock, zone).minusDays(days));
    }

    public boolean isReconstructed() {
        return this == Y5 || this == MAX;
    }
}
