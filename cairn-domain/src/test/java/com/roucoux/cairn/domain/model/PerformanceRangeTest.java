package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PerformanceRangeTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK =
            Clock.fixed(LocalDate.of(2026, 9, 24).atTime(20, 0).atZone(ZONE).toInstant(), ZONE);

    @Test
    void sevenDaysGoesBackSevenDaysFromToday() {
        assertThat(PerformanceRange.D7.from(CLOCK, ZONE)).contains(LocalDate.of(2026, 9, 17));
    }

    @Test
    void oneYearGoesBackThreeHundredAndSixtySixDaysFromToday() {
        assertThat(PerformanceRange.Y1.from(CLOCK, ZONE)).contains(LocalDate.of(2025, 9, 23));
    }

    @Test
    void fiveYearsGoesBackEighteenHundredAndTwentySevenDaysFromToday() {
        assertThat(PerformanceRange.Y5.from(CLOCK, ZONE)).contains(LocalDate.of(2021, 9, 23));
    }

    @Test
    void maxHasNoLowerBound() {
        assertThat(PerformanceRange.MAX.from(CLOCK, ZONE)).isEmpty();
    }

    @Test
    void onlyFiveYearsAndMaxAreReconstructed() {
        assertThat(PerformanceRange.D1.isReconstructed()).isFalse();
        assertThat(PerformanceRange.D7.isReconstructed()).isFalse();
        assertThat(PerformanceRange.M1.isReconstructed()).isFalse();
        assertThat(PerformanceRange.Y1.isReconstructed()).isFalse();
        assertThat(PerformanceRange.Y5.isReconstructed()).isTrue();
        assertThat(PerformanceRange.MAX.isReconstructed()).isTrue();
    }
}
