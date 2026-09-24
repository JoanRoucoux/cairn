package com.roucoux.cairn.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Aligned with the front's {@code rangeStart} (chart-range.ts): the number of days back from
 * today, in the given zone. {@code MAX} has no lower bound; each line is valued from its own
 * first known quote instead.
 */
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
